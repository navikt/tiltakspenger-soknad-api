package no.nav.tiltakspenger.soknad.api

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.jobber.TaskResultat
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
 */
class BakgrunnsprosesserTest {
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

        oppsett.mdcCallIdKey shouldBe CALL_ID_MDC_KEY
        oppsett.tasks.size shouldBe 3
        oppsett.kafkaConsumers shouldBe emptyList()
        oppsett.clock shouldBe tac.clock
    }
}
