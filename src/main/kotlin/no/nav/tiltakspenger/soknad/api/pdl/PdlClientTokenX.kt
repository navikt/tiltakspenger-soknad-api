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
import no.nav.tiltakspenger.soknad.api.httpClientCIO

const val INDIVIDSTONAD = "IND"

class PdlClientTokenX(
    config: ApplicationConfig,
    private val httpClient: HttpClient = httpClientCIO(),
) {
    private val pdlEndpoint = config.property("endpoints.pdl").getString()
    private val pdlAudience = config.property("audience.pdl").getString()
    private val oauth2ClientTokenX = checkNotNull(ClientConfig(config, httpClientCIO()).clients["tokendings"])
    val LOG = KotlinLogging.logger {}

    suspend fun fetchSøker(fødselsnummer: String, subjectToken: String): Result<SøkerRespons> {
        LOG.error { "Dette er en test" }
        val tokenResponse = oauth2ClientTokenX.tokenExchange(subjectToken, pdlAudience)
        val token = tokenResponse.accessToken
        val pdlResponse: Result<SøkerRespons> = kotlin.runCatching {
            httpClient.post(pdlEndpoint) {
                accept(ContentType.Application.Json)
                header("Tema", INDIVIDSTONAD)
                header("behandlingsnummer", "B470")
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(hentPersonQuery(fødselsnummer))
            }.let {
                LOG.error { "Dette er response før det blir gjort om til 'Body' $it" }
                return it.body()
            }
        }
        LOG.error { "Dette er pdl-body: $pdlResponse" }
        return pdlResponse
    }
}
