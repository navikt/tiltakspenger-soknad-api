package no.nav.tiltakspenger.soknad.api.tiltak

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO
import no.nav.tiltakspenger.soknad.api.TILTAK_PATH
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.FORTROLIG
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.STRENGT_FORTROLIG
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND
import no.nav.tiltakspenger.soknad.api.testutils.jsonKlient
import no.nav.tiltakspenger.soknad.api.testutils.leggIKøStatusForAlleForsøk
import no.nav.tiltakspenger.soknad.api.testutils.medTestApplikasjon
import no.nav.tiltakspenger.soknad.api.testutils.nyttTestFødselsnummer
import no.nav.tiltakspenger.soknad.api.testutils.søkerRespons
import no.nav.tiltakspenger.soknad.api.testutils.tiltakshistorikk
import org.junit.jupiter.api.Test
import java.io.IOException

class TiltakRoutesTest {
    private val testFødselsnummer = nyttTestFødselsnummer()

    @Test
    fun `get på tiltak-endepunkt svarer med tiltak når tokenet er gyldig`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.tiltakTransport.leggIKøJson(listOf(tiltakshistorikk(arrangør = "Testarrangør AS")))

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.OK
            val tiltak = response.body<TiltakDto>().tiltak.single()
            tiltak.aktivitetId shouldBe "123456"
            tiltak.type shouldBe TiltakResponsDTO.TiltakTypeDTO.ABOPPF
            tiltak.arrangør shouldBe "Testarrangør AS"
            tiltak.visningsnavn shouldBe "typenavn hos Testarrangør AS"
        }
    }

    @Test
    fun `get på tiltak-endepunkt godtar token med gammelt acr-claim`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer, acr = "Level4")
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.tiltakTransport.leggIKøJson(listOf(tiltakshistorikk()))

            jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }.status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `get på tiltak-endepunkt fjerner arrangørnavn for søker med adressebeskyttelse`() {
        // Én kontekst per gradering: PdlClient cacher søkeroppslaget på fødselsnummer.
        listOf(FORTROLIG, STRENGT_FORTROLIG, STRENGT_FORTROLIG_UTLAND).forEach { gradering ->
            medTestApplikasjon { tac ->
                val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
                tac.pdlTransport.leggIKøJson(søkerRespons(gradering = gradering))
                tac.tiltakTransport.leggIKøJson(listOf(tiltakshistorikk(arrangør = "Testarrangør AS")))

                val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

                response.status shouldBe HttpStatusCode.OK
                val tiltak = response.body<TiltakDto>().tiltak.single()
                tiltak.arrangør shouldBe ""
                tiltak.visningsnavn shouldBe "typenavn"
            }
        }
    }

    @Test
    fun `get på tiltak-endepunkt henter tiltak for fødselsnummeret i pid-claimet`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.tiltakTransport.leggIKøJson(listOf(tiltakshistorikk()))

            jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            tac.pdlTransport.mottatteKall.single().bodyTekst shouldContain testFødselsnummer
            val tiltakKall = tac.tiltakTransport.mottatteKall.single()
            tiltakKall.uri.toString() shouldBe "http://tiltak.test/tokenx/tiltakshistorikk"
            tiltakKall.request.headers().firstValue("Authorization").get() shouldBe "Bearer test-access-token"
        }
    }

    @Test
    fun `get på tiltak-endepunkt svarer 401 når token mangler`() {
        medTestApplikasjon {
            jsonKlient().get(TILTAK_PATH).status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `get på tiltak-endepunkt svarer 401 for token som ikke er utstedt til oss`() {
        medTestApplikasjon {
            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer ukjent-token") }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `get på tiltak-endepunkt svarer 401 når acr-claimet mangler`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer, acr = "")

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `get på tiltak-endepunkt svarer 500 når pdl-kallet feiler`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøKast(IOException("pdl er nede"))

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `get på tiltak-endepunkt svarer 500 når tiltak-kallet feiler`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.tiltakTransport.leggIKøStatusForAlleForsøk(500, "tiltak er nede")

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `get på tiltak-endepunkt svarer 500 når søkeren er registrert som død i PDL`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            // Mappingen kaster på død søker; ruta skal ta det som en uventet feil og svare 500.
            tac.pdlTransport.leggIKøJson(søkerRespons(død = true))

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }
}
