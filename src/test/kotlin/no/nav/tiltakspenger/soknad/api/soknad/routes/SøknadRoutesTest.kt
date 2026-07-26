package no.nav.tiltakspenger.soknad.api.soknad.routes

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.tiltakspenger.soknad.api.soknad.Applikasjonseier
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import no.nav.tiltakspenger.soknad.api.soknad.Tiltak
import no.nav.tiltakspenger.soknad.api.soknad.validering.spørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.soknad.validering.toJsonString
import no.nav.tiltakspenger.soknad.api.testutils.TestApplicationContext
import no.nav.tiltakspenger.soknad.api.testutils.enkelPdf
import no.nav.tiltakspenger.soknad.api.testutils.medTestApplikasjon
import no.nav.tiltakspenger.soknad.api.testutils.postSøknad
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class SøknadRoutesTest {
    private val testFødselsnummer = "12345678910"

    @Test
    fun `post uten token gir 401`() {
        medTestApplikasjon {
            postSøknad(token = null).status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post med ukjent token gir 401`() {
        medTestApplikasjon {
            postSøknad(token = "ugyldigtoken").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post med token som har for lavt acr-nivå gir 401`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer, acr = "Level3")

            postSøknad(token).status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post med gyldig søknad uten vedlegg gir 201 og lagrer søknaden`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)

            postSøknad(token).status shouldBe HttpStatusCode.Created

            val lagret = tac.søknadRepo.hentBrukersSøknader(testFødselsnummer, Applikasjonseier.Tiltakspenger).single()
            lagret.fnr shouldBe testFødselsnummer
            lagret.acr shouldBe "idporten-loa-high"
            lagret.vedlegg shouldBe emptyList()
        }
    }

    @Test
    fun `post med vedlegg virussjekker vedlegget og lagrer det`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.clamAvTransport.leggIKøJson("""[{"Filename":"vedlegg.pdf","Result":"OK"}]""")

            postSøknad(token, vedlegg = enkelPdf()).status shouldBe HttpStatusCode.Created

            tac.clamAvTransport.mottatteKall.single().bodyTekst.contains("vedlegg.pdf") shouldBe true
            val lagret = tac.søknadRepo.alle.single()
            lagret.vedlegg.single().filnavn shouldBe "vedlegg.pdf"
            lagret.vedlegg.single().contentType shouldBe "application/pdf"
        }
    }

    @Test
    fun `post med vedlegg som inneholder skadevare gir 400 og lagrer ingenting`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.clamAvTransport.leggIKøJson("""[{"Filename":"vedlegg.pdf","Result":"FOUND"}]""")

            postSøknad(token, vedlegg = enkelPdf()).status shouldBe HttpStatusCode.BadRequest

            tac.søknadRepo.alle shouldBe emptyList()
        }
    }

    @Test
    fun `post gir 500 når virusskanningen av en fil feiler`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.clamAvTransport.leggIKøJson("""[{"Filename":"vedlegg.pdf","Result":"ERROR"}]""")

            postSøknad(token, vedlegg = enkelPdf()).status shouldBe HttpStatusCode.InternalServerError

            tac.søknadRepo.alle shouldBe emptyList()
        }
    }

    @Test
    fun `post gir 500 når kallet til ClamAV feiler`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.clamAvTransport.leggIKøStatus(503, "clamav er nede")

            postSøknad(token, vedlegg = enkelPdf()).status shouldBe HttpStatusCode.InternalServerError

            tac.søknadRepo.alle shouldBe emptyList()
        }
    }

    @Test
    fun `post gir 500 når lagring av søknaden feiler`() {
        val tac = TestApplicationContext()
        tac.søknadRepo.kastVedLagring = RuntimeException("databasen er nede")

        medTestApplikasjon(tac) {
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)

            postSøknad(token).status shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `post uten søknad-part gir 400`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)

            postSøknad(token, søknadJson = null).status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `post med ukjent form-item gir 400`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)

            postSøknad(token, søknadJson = null, ukjentFormItem = true).status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `post med søknad som ikke validerer gir 400`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            // Fra-dato etter til-dato: brytes av valideringen i taInnSøknadSomMultipart.
            val ugyldigTiltak = Tiltak(
                aktivitetId = "123",
                periode = Periode(fra = LocalDate.of(2025, 1, 2), til = LocalDate.of(2025, 1, 1)),
                arenaRegistrertPeriode = null,
                arrangør = "test",
                type = "test",
                typeNavn = "test",
                gjennomforingId = null,
                visningsnavn = null,
            )
            val ugyldig = spørsmålsbesvarelser(tiltak = ugyldigTiltak)

            postSøknad(token, søknadJson = ugyldig.toJsonString()).status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `post med vedlegg av ikke-støttet filtype gir 500`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)

            val response = postSøknad(token, vedlegg = "ikke en pdf".toByteArray())

            response.status shouldBe HttpStatusCode.InternalServerError
            tac.søknadRepo.alle shouldBe emptyList()
        }
    }
}
