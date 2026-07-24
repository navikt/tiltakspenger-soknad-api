package no.nav.tiltakspenger.soknad.api.soknad

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.json.serialize
import no.nav.tiltakspenger.soknad.api.installJacksonFeature
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import org.junit.jupiter.api.Test

/**
 * Dekker valideringslambdaen som registreres i ktor sitt RequestValidation-plugin.
 * Selve valideringsreglene dekkes av testene i `validering`-pakken.
 */
class SøknadValidationKtTest {
    private fun io.ktor.server.testing.ApplicationTestBuilder.settOppValideringsApp() {
        application {
            install(RequestValidation) { validateSøknad(fixedClock) }
            installJacksonFeature()
            routing {
                post("/valider") {
                    call.receive<SpørsmålsbesvarelserDTO>()
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }

    @Test
    fun `gyldig søknad passerer request-valideringen`() = testApplication {
        settOppValideringsApp()

        val response = client.post("/valider") {
            contentType(ContentType.Application.Json)
            setBody(serialize(mockSpørsmålsbesvarelser()))
        }

        response.status shouldBe HttpStatusCode.OK
    }

    @Test
    fun `ugyldig søknad avvises av request-valideringen`() = testApplication {
        settOppValideringsApp()

        val response = client.post("/valider") {
            contentType(ContentType.Application.Json)
            setBody(serialize(mockSpørsmålsbesvarelser(harBekreftetAlleOpplysninger = false)))
        }

        // RequestValidationException håndteres ikke av noen StatusPages i denne testappen; ktor svarer 500.
        response.status shouldBe HttpStatusCode.InternalServerError
    }
}
