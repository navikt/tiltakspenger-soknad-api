package no.nav.tiltakspenger.soknad.api.pdl

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.config.ApplicationConfig
import mu.KotlinLogging
import no.nav.tiltakspenger.soknad.api.auth.oauth.ClientConfig
import no.nav.tiltakspenger.soknad.api.httpClientWithRetry

class PdlCredentialsClient(
    config: ApplicationConfig,
    private val httpClient: HttpClient = httpClientWithRetry(timeout = 10L),
) {
    private val pdlEndpoint = config.property("endpoints.pdl").getString()
    private val pdlScope = config.property("scope.pdl").getString()
    private val oauth2CredentialsClient = checkNotNull(ClientConfig(config, httpClientWithRetry()).clients["azure"])
    private val log = KotlinLogging.logger {}

    suspend fun fetchBarn(ident: String, callId: String): Result<SøkersBarnRespons> {
        log.info { "fetchBarn: Henter credentials for å snakke med PDL" }
        val token = oauth2CredentialsClient.clientCredentials(pdlScope).token
        log.info { "fetchBarn: Hent credentials OK" }
        return kotlin.runCatching {
            httpClient.post(pdlEndpoint) {
                accept(ContentType.Application.Json)
                header("Tema", INDIVIDSTONAD)
                header("Nav-Call-Id", callId)
                header("behandlingsnummer", "B470")
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(hentBarnQuery(ident))
            }.body()
        }
    }
}
