package no.nav.tiltakspenger.soknad.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.pdl.routes.dto.PersonDTO
import no.nav.tiltakspenger.soknad.api.testutils.FakeSøknadRepo
import no.nav.tiltakspenger.soknad.api.testutils.enkelPdf
import no.nav.tiltakspenger.soknad.api.testutils.jsonKlient
import no.nav.tiltakspenger.soknad.api.testutils.medApplikasjon
import no.nav.tiltakspenger.soknad.api.testutils.postSøknad
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakDto
import org.junit.jupiter.api.Test

/**
 * Lokal kjøring står og faller på wiringen i [LokalApplicationContext], så testen kjører hele søknadsflyten mot nøyaktig den — ikke mot test-konteksten.
 * Da kan verken fakene eller `LokalMain` råtne i det stille.
 *
 * Eneste forskjell fra det [LokalMain] starter, er at søknadene lagres i en fake i stedet for postgres.
 * Klientene fakene erstatter, dekkes av sine egne tester over `FakeHttpTransport`.
 */
internal class LokalApplicationContextTest {
    private val fnr = "12345678910"

    private fun lokalKontekst() = LokalApplicationContext(
        clock = fixedClock,
        søknadRepo = FakeSøknadRepo(),
        fnr = fnr,
        collectorRegistry = CollectorRegistry(),
    )

    @Test
    fun `hele søknadsflyten virker med de lokale fakene`() {
        val context = lokalKontekst()

        medApplikasjon(context) {
            // TexasClientFakeLokal godtar hvilket som helst token, slik at authserveren ikke trengs.
            val token = "et-hvilket-som-helst-token"

            val personalia = jsonKlient().get(PERSONALIA_PATH) { header("Authorization", "Bearer $token") }
            personalia.status shouldBe HttpStatusCode.OK
            val person = personalia.body<PersonDTO>()
            person.fornavn shouldBe "Lokal"
            person.harFylt18År shouldBe true
            // Begge barna er under 16 år og skal kunne velges i barnetillegg.
            person.barn.size shouldBe 2

            val tiltak = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }
            tiltak.status shouldBe HttpStatusCode.OK
            val tiltaksdeltakelse = tiltak.body<TiltakDto>().tiltak.single()
            tiltaksdeltakelse.arrangør shouldBe "Lokal arrangør AS"
            // Søknaden lar deg ikke velge et tiltak som mangler en av datoene, og da stopper utfyllingen på tiltakssteget.
            tiltaksdeltakelse.arenaRegistrertPeriode.fra shouldNotBe null
            tiltaksdeltakelse.arenaRegistrertPeriode.til shouldNotBe null

            // Vedlegget virussjekkes mot den samme fake-en før søknaden lagres.
            postSøknad(token, vedlegg = listOf(enkelPdf())).status shouldBe HttpStatusCode.Created
        }

        val søknadRepo = context.søknadRepo as FakeSøknadRepo
        val søknad = søknadRepo.alle.single()
        søknad.fnr shouldBe fnr
        søknad.vedlegg.single().filnavn shouldBe "vedlegg-1.pdf"

        // Jobbene tar søknaden hele veien til «sendt til saksbehandling».
        runBlocking {
            val correlationId = CorrelationId.generate()
            jobber(context).forEach { it.utfør(correlationId) }
        }

        val behandlet = søknadRepo.hentSøknad(søknad.id)!!
        behandlet.saksnummer shouldNotBe null
        behandlet.journalpostId shouldNotBe null
        behandlet.journalført shouldNotBe null
        behandlet.sendtTilVedtak shouldNotBe null
    }
}
