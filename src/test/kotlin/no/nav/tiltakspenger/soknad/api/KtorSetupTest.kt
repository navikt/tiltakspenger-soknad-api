package no.nav.tiltakspenger.soknad.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import no.nav.tiltakspenger.soknad.api.testutils.medTestApplikasjon
import org.junit.jupiter.api.Test

class KtorSetupTest {
    @Test
    fun `ktorSetup setter opp helseruter, metrics, call-logging og autentiserte ruter`() {
        medTestApplikasjon { _ ->
            client.get("/isalive").status shouldBe HttpStatusCode.OK
            // En nyopprettet Readiness er ikke klar før livssyklusen har satt den klar.
            client.get("/isready").status shouldBe HttpStatusCode.ServiceUnavailable
            client.get("/metrics").status shouldBe HttpStatusCode.OK
            // Uten gyldig token svarer de autentiserte rutene 401.
            // Kallene mot søknad-, personalia- og tiltak-stiene treffer samtidig CallLogging-filteret og formatteren.
            client.post(SØKNAD_PATH).status shouldBe HttpStatusCode.Unauthorized
            client.get(PERSONALIA_PATH).status shouldBe HttpStatusCode.Unauthorized
            client.get(TILTAK_PATH).status shouldBe HttpStatusCode.Unauthorized
        }
    }
}
