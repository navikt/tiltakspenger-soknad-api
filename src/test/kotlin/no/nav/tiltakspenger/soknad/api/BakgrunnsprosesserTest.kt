package no.nav.tiltakspenger.soknad.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.ServerReady
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.jobber.TaskResultat
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Readiness
import no.nav.tiltakspenger.libs.ktor.common.oppstart.konfigurerOppstart
import no.nav.tiltakspenger.soknad.api.soknad.Applikasjonseier
import no.nav.tiltakspenger.soknad.api.soknad.validering.søknad
import no.nav.tiltakspenger.soknad.api.testutils.TestApplicationContext
import no.nav.tiltakspenger.soknad.api.testutils.enkelPdf
import no.nav.tiltakspenger.soknad.api.testutils.søkerRespons
import no.nav.tiltakspenger.soknad.api.util.genererMottattSøknadForTest
import org.junit.jupiter.api.Test

/**
 * Bakgrunnsprosessene settes opp likt i drift, lokalt og i test.
 * Testen kjører selve task-lambdaene, slik at et navn ikke kan peke på feil jobb uten at det oppdages.
 * Til slutt kjøres hele oppkoblingen i et Ktor-testoppsett, slik at målingene fra jobbene og meldingsleseren kan leses tilbake fra `/metrics`.
 */
class BakgrunnsprosesserTest {
    private val log = KotlinLogging.logger { }
    private val correlationId = CorrelationId.generate()

    @Test
    fun `jobbene har de forventede navnene og intervallene`() {
        val tasks = jobber(TestApplicationContext())

        tasks.map { it.navn } shouldContainExactly listOf(
            "soknad-jobb-hent-saksnummer",
            "soknad-jobb-journalfør",
            "soknad-jobb-send-til-saksbehandling",
        )
        tasks.forEach { it.intervall.forMiljø(isNais = true).inWholeSeconds shouldBe 60 }
    }

    @Test
    fun `hent-saksnummer-jobben henter saksnummer for søknadene som mangler det`() = runTest {
        val tac = TestApplicationContext()
        val søknad = genererMottattSøknadForTest(eier = Applikasjonseier.Tiltakspenger, saksnummer = null)
        tac.søknadRepo.lagre(søknad)
        tac.saksbehandlingApiTransport.leggIKøJson("""{"saksnummer":"1234"}""")

        val resultat = jobber(tac).single { it.navn == "soknad-jobb-hent-saksnummer" }.utfør(correlationId)

        resultat shouldBe TaskResultat.Ferdig
        tac.søknadRepo.hentSøknad(søknad.id)?.saksnummer shouldBe "1234"
    }

    @Test
    fun `journalfør-jobben journalfører søknadene som er klare`() = runTest {
        val tac = TestApplicationContext()
        val søknad = genererMottattSøknadForTest(
            eier = Applikasjonseier.Tiltakspenger,
            saksnummer = "1234",
            vedlegg = emptyList(),
        )
        tac.søknadRepo.lagre(søknad)
        tac.pdlTransport.leggIKøJson(søkerRespons())
        tac.pdfTransport.leggIKøBytes(enkelPdf(), contentType = "application/pdf")
        tac.dokarkivTransport.leggIKøJson("""{"journalpostId":"15","journalpostferdigstilt":true}""")

        val resultat = jobber(tac).single { it.navn == "soknad-jobb-journalfør" }.utfør(correlationId)

        resultat shouldBe TaskResultat.Ferdig
        tac.søknadRepo.hentSøknad(søknad.id)?.journalført shouldNotBe null
    }

    @Test
    fun `send-til-saksbehandling-jobben sender de journalførte søknadene`() = runTest {
        val tac = TestApplicationContext()
        val opprettet = nå(fixedClock)
        val søknad = genererMottattSøknadForTest(
            opprettet = opprettet,
            eier = Applikasjonseier.Tiltakspenger,
            saksnummer = "1234",
            vedlegg = emptyList(),
        ).copy(
            søknad = søknad(),
            journalpostId = JournalpostId("15"),
            journalført = opprettet,
        )
        tac.søknadRepo.lagre(søknad)
        tac.saksbehandlingApiTransport.leggIKøTomRespons(statusCode = 200)

        val resultat = jobber(tac).single { it.navn == "soknad-jobb-send-til-saksbehandling" }.utfør(correlationId)

        resultat shouldBe TaskResultat.Ferdig
        tac.søknadRepo.hentSøknad(søknad.id)?.sendtTilVedtak shouldNotBe null
    }

    @Test
    fun `identhendelse-consumeren settes bare opp i Nais`() {
        val tac = TestApplicationContext()

        kafkaConsumers(isNais = false, applicationContext = tac) shouldBe emptyList()
        kafkaConsumers(isNais = true, applicationContext = tac).single().navn shouldBe "identhendelse-consumer"
    }

    @Test
    fun `consumer-oppsettet starter og stopper consumeren fra konteksten`() {
        val tac = TestApplicationContext()
        val oppsett = kafkaConsumers(isNais = true, applicationContext = tac).single()

        // run() er ikke-blokkerende og trenger ingen broker; stop() venter på at loopen er ferdig.
        shouldNotThrowAny {
            oppsett.start()
            oppsett.stopp()
        }
    }

    @Test
    fun `oppsettet knytter jobbene og consumerne til konteksten`() {
        val tac = TestApplicationContext()

        val oppsett = bakgrunnsprosessoppsett(applicationContext = tac, isNais = false)

        val jobboppsett = oppsett.jobber.shouldNotBeNull()
        jobboppsett.mdcCallIdKey shouldBe CALL_ID_MDC_KEY
        jobboppsett.tasks.size shouldBe 3
        jobboppsett.clock shouldBe tac.clock
        jobboppsett.meterRegistry shouldBe tac.meterRegistry
        oppsett.kafkaConsumers shouldBe emptyList()
    }

    /**
     * Verifiserer at registeret jobbene og meldingsleseren skriver målingene sine til, er det samme registeret `/metrics` skraper.
     * Det er hele poenget med at [ApplicationContext] eier registeret: sender vi inn et annet register i `Jobboppsett` eller i consumeren, forsvinner seriene stille, og varselreglene «Jobb har stoppet» og «Meldingsleser har stoppet» får aldri data.
     *
     * Consumeren konstrueres, men startes ikke.
     * Meldingsleser-målingene registreres i konstruktøren til `ManagedKafkaConsumer`, mens `run()` ville krevd en ekte Kafka-broker.
     * Jobbmålingene registreres når skedulereren starter, altså ved [ServerReady].
     */
    @Test
    fun `jobbene og consumeren fører målingene sine i registeret metrics skraper`() = testApplication {
        val tac = TestApplicationContext()
        val readiness = Readiness()
        lateinit var app: Application
        application {
            app = this
            ktorSetup(tac, readiness)
            konfigurerOppstart(
                log = log,
                isNais = false,
                readiness = readiness,
                // Gir de tre ekte jobbene og en tom consumer-liste; consumerne startes ikke her, det ville krevd en ekte broker.
                oppsett = bakgrunnsprosessoppsett(applicationContext = tac, isNais = false),
            )
        }

        // `application { }` er lat i testoppsettet, så appen må startes eksplisitt før `app` er satt.
        startApplication()

        // Konstruerer consumeren uten å starte den, slik at meldingsleser-målingene registreres på kontekstens register.
        tac.identhendelseConsumer

        app.monitor.raise(ServerReady, app.environment)

        client.get("/metrics").apply {
            status shouldBe HttpStatusCode.OK
            val metrikker = bodyAsText()
            metrikker shouldContain
                """tpts_bakgrunnsprosess_intervall_sekunder{prosess="soknad-jobb-journalfør",type="jobb"}"""
            metrikker shouldContain
                """tpts_bakgrunnsprosess_sist_vellykket_tidspunkt_sekunder{prosess="soknad-jobb-journalfør",type="jobb"}"""
            metrikker shouldContain
                """tpts_bakgrunnsprosess_intervall_sekunder{prosess="tpts.identhendelse-v1",type="meldingsleser"}"""
            metrikker shouldContain
                """tpts_bakgrunnsprosess_sist_vellykket_tidspunkt_sekunder{prosess="tpts.identhendelse-v1",type="meldingsleser"}"""
            // Ktor-metrikkene ligger i det samme registeret, som bevis på at det er Ktor-oppsettets register vi skraper.
            metrikker shouldContain "ktor_http_server_requests"
        }

        app.monitor.raise(ApplicationStopping, app)
    }
}
