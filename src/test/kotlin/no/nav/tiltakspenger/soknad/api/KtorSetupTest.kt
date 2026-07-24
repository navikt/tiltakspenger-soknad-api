package no.nav.tiltakspenger.soknad.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Readiness
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.libs.texas.client.TexasIntrospectionResponse
import org.junit.jupiter.api.Test

class KtorSetupTest {
    @Test
    fun `ktorSetup setter opp helseruter, metrics, call-logging og autentiserte ruter`() = testApplication {
        val texasClient = mockk<TexasHttpClient>()
        coEvery { texasClient.introspectToken(any(), any()) } returns TexasIntrospectionResponse(
            active = false,
            error = "Ugyldig token",
            groups = null,
            roles = null,
        )
        application {
            ktorSetup(
                texasClient = texasClient,
                pdlService = mockk(),
                tiltakService = mockk(),
                avService = mockk(),
                metricsCollector = mockk(relaxed = true),
                nySøknadService = mockk(),
                readiness = Readiness(),
                clock = fixedClock,
            )
        }

        client.get("/isalive").status shouldBe HttpStatusCode.OK
        // En nyopprettet Readiness er ikke klar før livssyklusen har satt den klar.
        client.get("/isready").status shouldBe HttpStatusCode.ServiceUnavailable
        client.get("/metrics").status shouldBe HttpStatusCode.OK
        // Uten gyldig token svarer de autentiserte rutene 401; kall mot SØKNAD_PATH treffer også CallLogging-filteret og formatteren.
        client.post(SØKNAD_PATH).status shouldBe HttpStatusCode.Unauthorized
        client.get(PERSONALIA_PATH).status shouldBe HttpStatusCode.Unauthorized
    }
}
