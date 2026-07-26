package no.nav.tiltakspenger.soknad.api.testutils

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.jackson3.JacksonConverter
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.tiltakspenger.libs.json.objectMapper
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Readiness
import no.nav.tiltakspenger.soknad.api.ktorSetup

/**
 * Starter en test-app med det ekte [ktorSetup]-oppsettet over en [TestApplicationContext].
 * Testen får konteksten inn, og bruker den til å køe HTTP-svar, registrere tokens og inspisere lagrede søknader.
 */
fun medTestApplikasjon(
    tac: TestApplicationContext = TestApplicationContext(),
    testBlock: suspend ApplicationTestBuilder.(TestApplicationContext) -> Unit,
) {
    testApplication {
        application {
            // Readiness settes klar av livssyklusen i startApp, som ikke kjører her; /isready svarer derfor 503 i testene.
            ktorSetup(tac, Readiness())
        }
        testBlock(tac)
    }
}

/** Test-klient som deserialiserer JSON-responser med den delte [objectMapper]-en. */
fun ApplicationTestBuilder.jsonKlient(): HttpClient = createClient {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(objectMapper))
    }
}
