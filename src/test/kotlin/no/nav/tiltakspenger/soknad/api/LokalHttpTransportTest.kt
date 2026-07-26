package no.nav.tiltakspenger.soknad.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.pdl.routes.dto.PersonDTO
import no.nav.tiltakspenger.soknad.api.testutils.LokalHttpTransport
import no.nav.tiltakspenger.soknad.api.testutils.TestApplicationContext
import no.nav.tiltakspenger.soknad.api.testutils.enkelPdf
import no.nav.tiltakspenger.soknad.api.testutils.jsonKlient
import no.nav.tiltakspenger.soknad.api.testutils.medTestApplikasjon
import no.nav.tiltakspenger.soknad.api.testutils.postSøknad
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakDto
import org.junit.jupiter.api.Test

/**
 * Lokal kjøring står og faller på at [LokalHttpTransport] svarer noe klientene faktisk kan lese.
 * Testen kjører hele søknadsflyten mot den, så `LokalMain` ikke kan råtne i det stille.
 *
 * Eneste forskjell fra `LokalApplicationContext` er at søknadene lagres i en fake i stedet for postgres.
 */
internal class LokalHttpTransportTest {
    private val fnr = "12345678910"

    private fun lokalKontekst() = TestApplicationContext(
        // ClamAV-klienten i test-konteksten kalles på /scan, mens den lokale konfigurasjonen bruker /av.
        fellesTransport = LokalHttpTransport(clock = fixedClock, fnr = fnr, avPath = "/scan"),
    )

    @Test
    fun `hele søknadsflyten virker med de lokale fakene`() {
        val tac = lokalKontekst()

        medTestApplikasjon(tac) {
            val token = tac.texasClient.leggTilBrukertoken(fnr)

            val personalia = jsonKlient().get(PERSONALIA_PATH) { header("Authorization", "Bearer $token") }
            personalia.status shouldBe HttpStatusCode.OK
            val person = personalia.body<PersonDTO>()
            person.fornavn shouldBe "Lokal"
            person.harFylt18År shouldBe true
            // Begge barna er under 16 år og skal kunne velges i barnetillegg.
            person.barn.size shouldBe 2

            val tiltak = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }
            tiltak.status shouldBe HttpStatusCode.OK
            tiltak.body<TiltakDto>().tiltak.single().arrangør shouldBe "Lokal arrangør AS"

            // Vedlegget virussjekkes mot den samme transporten før søknaden lagres.
            postSøknad(token, vedlegg = enkelPdf()).status shouldBe HttpStatusCode.Created
        }

        val søknad = tac.søknadRepo.alle.single()
        søknad.fnr shouldBe fnr
        søknad.vedlegg.single().filnavn shouldBe "vedlegg.pdf"

        // Jobbene tar søknaden hele veien til «sendt til saksbehandling».
        runBlocking {
            val correlationId = CorrelationId.generate()
            jobber(tac).forEach { it.utfør(correlationId) }
        }

        val behandlet = tac.søknadRepo.hentSoknad(søknad.id)!!
        behandlet.saksnummer shouldNotBe null
        behandlet.journalpostId shouldNotBe null
        behandlet.journalført shouldNotBe null
        behandlet.sendtTilVedtak shouldNotBe null
    }
}
