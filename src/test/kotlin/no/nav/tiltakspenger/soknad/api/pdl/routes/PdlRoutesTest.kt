package no.nav.tiltakspenger.soknad.api.pdl.routes

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import no.nav.tiltakspenger.soknad.api.PERSONALIA_PATH
import no.nav.tiltakspenger.soknad.api.pdl.routes.dto.PersonDTO
import no.nav.tiltakspenger.soknad.api.testutils.barnRespons
import no.nav.tiltakspenger.soknad.api.testutils.jsonKlient
import no.nav.tiltakspenger.soknad.api.testutils.leggIKøStatusForAlleForsøk
import no.nav.tiltakspenger.soknad.api.testutils.medTestApplikasjon
import no.nav.tiltakspenger.soknad.api.testutils.søkerRespons
import no.nav.tiltakspenger.soknad.api.testutils.tiltakshistorikk
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.LocalDate

class PdlRoutesTest {
    private val testFødselsnummer = "12345678910"
    private val barnFødselsnummer = "02062012345"

    @Test
    fun `get på personalia-endepunkt svarer med personalia fra PDL når tokenet er gyldig`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.tiltakTransport.leggIKøJson(listOf(tiltakshistorikk()))
            tac.pdlTransport.leggIKøJson(søkerRespons(fornavn = "foo", mellomnavn = "baz", etternavn = "bar"))

            val response = jsonKlient().get(PERSONALIA_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.OK
            val body: PersonDTO = response.body()
            body.fornavn shouldBe "foo"
            body.mellomnavn shouldBe "baz"
            body.etternavn shouldBe "bar"
            body.harFylt18År shouldBe true
            body.barn shouldBe emptyList()
        }
    }

    @Test
    fun `get på personalia-endepunkt tar med barn under 16 år`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.tiltakTransport.leggIKøJson(listOf(tiltakshistorikk()))
            tac.pdlTransport.leggIKøJson(søkerRespons(barnIdenter = listOf(barnFødselsnummer)))
            tac.pdlTransport.leggIKøJson(barnRespons(ident = barnFødselsnummer))

            val response = jsonKlient().get(PERSONALIA_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.OK
            val barn = response.body<PersonDTO>().barn.single()
            barn.fornavn shouldBe "Barn"
            barn.etternavn shouldBe "Barnesen"
        }
    }

    @Test
    fun `get på personalia-endepunkt filtrerer barn på tiltakets tidligste fra-dato, ikke dagens dato`() {
        // Barnet fyller 16 mellom tiltakets fra-dato og dagens dato, og skal dermed regnes som under 16.
        val barnSomFyller16 = "15120812345"
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.tiltakTransport.leggIKøJson(listOf(tiltakshistorikk(deltakelseFom = LocalDate.of(2024, 12, 1))))
            tac.pdlTransport.leggIKøJson(søkerRespons(barnIdenter = listOf(barnSomFyller16)))
            tac.pdlTransport.leggIKøJson(barnRespons(ident = barnSomFyller16, fødselsdato = "2008-12-15"))

            val response = jsonKlient().get(PERSONALIA_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.OK
            response.body<PersonDTO>().barn.single().fnr shouldBe barnSomFyller16
        }
    }

    @Test
    fun `get på personalia-endepunkt utelater barn som har fylt 16 på styrende dato`() {
        val barnSomHarFylt16 = "15120812345"
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.tiltakTransport.leggIKøJson(listOf(tiltakshistorikk()))
            tac.pdlTransport.leggIKøJson(søkerRespons(barnIdenter = listOf(barnSomHarFylt16)))
            tac.pdlTransport.leggIKøJson(barnRespons(ident = barnSomHarFylt16, fødselsdato = "2008-12-15"))

            val response = jsonKlient().get(PERSONALIA_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.OK
            response.body<PersonDTO>().barn shouldBe emptyList()
        }
    }

    @Test
    fun `get på personalia-endepunkt svarer 200 når PDL utelater folkeregistermetadata`() {
        // PDL sender folkeregistermetadata=null på opplysninger som ikke er mastret i Folkeregisteret.
        // Da DTO-ene krevde feltet, feilet hele oppslaget på deserialiseringen og ruta svarte 500.
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.tiltakTransport.leggIKøJson(listOf(tiltakshistorikk()))
            tac.pdlTransport.leggIKøJson(søkerRespons(barnIdenter = listOf(barnFødselsnummer), utenFregmeta = true))
            tac.pdlTransport.leggIKøJson(barnRespons(ident = barnFødselsnummer, utenFregmeta = true))

            val response = jsonKlient().get(PERSONALIA_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.OK
            val body: PersonDTO = response.body()
            body.fornavn shouldBe "Fornavn"
            body.barn.single().fornavn shouldBe "Barn"
        }
    }

    @Test
    fun `get på personalia-endepunkt slår opp fødselsnummeret i pid-claimet`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.tiltakTransport.leggIKøJson(listOf(tiltakshistorikk()))
            tac.pdlTransport.leggIKøJson(søkerRespons())

            jsonKlient().get(PERSONALIA_PATH) { header("Authorization", "Bearer $token") }

            val kall = tac.pdlTransport.mottatteKall.single()
            kall.uri.toString() shouldBe "http://pdl.test/graphql"
            kall.request.headers().firstValue("behandlingsnummer").get() shouldBe "B470"
        }
    }

    @Test
    fun `get på personalia-endepunkt svarer 401 når token mangler`() {
        medTestApplikasjon {
            jsonKlient().get(PERSONALIA_PATH).status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `get på personalia-endepunkt svarer 401 for token som ikke er utstedt til oss`() {
        medTestApplikasjon {
            val response = jsonKlient().get(PERSONALIA_PATH) { header("Authorization", "Bearer ukjent-token") }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `get på personalia-endepunkt svarer 401 når acr-claimet mangler`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer, acr = "")

            val response = jsonKlient().get(PERSONALIA_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `get på personalia-endepunkt svarer 500 når tiltak-kallet feiler`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.tiltakTransport.leggIKøStatusForAlleForsøk(500, "tiltak er nede")

            val response = jsonKlient().get(PERSONALIA_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `get på personalia-endepunkt svarer 500 når pdl-kallet feiler`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.tiltakTransport.leggIKøJson(listOf(tiltakshistorikk()))
            tac.pdlTransport.leggIKøKast(IOException("pdl er nede"))

            val response = jsonKlient().get(PERSONALIA_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `get på personalia-endepunkt svarer 500 når PDL svarer 200 med feil i errors-lista`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.tiltakTransport.leggIKøJson(listOf(tiltakshistorikk()))
            tac.pdlTransport.leggIKøJson("""{"errors":[{"message":"Ikke tilgang"}]}""")

            val response = jsonKlient().get(PERSONALIA_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `get på personalia-endepunkt svarer 500 når PDL svarer 200 uten data`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.tiltakTransport.leggIKøJson(listOf(tiltakshistorikk()))
            tac.pdlTransport.leggIKøJson("""{}""")

            val response = jsonKlient().get(PERSONALIA_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `get på personalia-endepunkt svarer 500 når søkeren er registrert som død i PDL`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.tiltakTransport.leggIKøJson(listOf(tiltakshistorikk()))
            // Mappingen kaster på død søker; ruta skal ta det som en uventet feil og svare 500.
            tac.pdlTransport.leggIKøJson(søkerRespons(død = true))

            val response = jsonKlient().get(PERSONALIA_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }
}
