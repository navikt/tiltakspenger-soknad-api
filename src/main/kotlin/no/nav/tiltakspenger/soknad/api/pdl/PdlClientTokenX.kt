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
import no.nav.tiltakspenger.soknad.api.extensions.getAccessTokenOrThrow
import no.nav.tiltakspenger.soknad.api.httpClientCIO
import no.nav.tiltakspenger.soknad.api.httpClientWithRetry

const val INDIVIDSTONAD = "IND"

class PdlClientTokenX(
    config: ApplicationConfig,
    private val httpClient: HttpClient = httpClientWithRetry(timeout = 10L),
) {
    private val pdlEndpoint = config.property("endpoints.pdl").getString()
    private val pdlAudience = config.property("audience.pdl").getString()
    private val oauth2ClientTokenX = checkNotNull(ClientConfig(config, httpClientCIO()).clients["tokendings"])
    private val log = KotlinLogging.logger {}

    suspend fun fetchSøker(fødselsnummer: String, subjectToken: String, callId: String): Result<SøkerRespons> {
        log.info { "fetchSøker: Henter token for å snakke med PDL" }
        val tokenResponse = oauth2ClientTokenX.tokenExchange(subjectToken, pdlAudience)
        log.info { "fetchSøker: Token-respons mottatt" }
        val token = tokenResponse.getAccessTokenOrThrow()
        log.info { "fetchSøker: Token-exchange OK" }
        val pdlResponse: Result<SøkerRespons> = kotlin.runCatching {
            httpClient.post(pdlEndpoint) {
                accept(ContentType.Application.Json)
                header("Tema", INDIVIDSTONAD)
                header("Nav-Call-Id", callId)
                header("behandlingsnummer", "B470")
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(hentPersonQuery(fødselsnummer))
            }.body()
        }
        return pdlResponse
    }

    suspend fun fetchAdressebeskyttelse(
        fødselsnummer: String,
        subjectToken: String,
        callId: String,
    ): Result<AdressebeskyttelseRespons> {
        log.debug { "fetchAdressebeskyttelse: Henter token for å snakke med PDL" }
        val tokenResponse = oauth2ClientTokenX.tokenExchange(subjectToken, pdlAudience)
        log.debug { "fetchAdressebeskyttelse:Token-respons mottatt" }
        val token = tokenResponse.getAccessTokenOrThrow()
        log.debug { "fetchAdressebeskyttelse: Token-exchange OK" }
        val pdlResponse: Result<AdressebeskyttelseRespons> = kotlin.runCatching {
            httpClient.post(pdlEndpoint) {
                accept(ContentType.Application.Json)
                header("Tema", INDIVIDSTONAD)
                header("Nav-Call-Id", callId)
                header("behandlingsnummer", "B470")
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(hentAdressebeskyttelseQuery(fødselsnummer))
            }.body()
        }
        return pdlResponse
    }
}
