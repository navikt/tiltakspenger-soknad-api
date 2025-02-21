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
import mu.KotlinLogging
import no.nav.tiltakspenger.soknad.api.auth.texas.client.TexasClient
import no.nav.tiltakspenger.soknad.api.httpClientWithRetry

const val INDIVIDSTONAD = "IND"

class PdlClientTokenX(
    private val httpClient: HttpClient = httpClientWithRetry(timeout = 10L),
    private val pdlEndpoint: String,
    private val pdlScope: String,
    private val texasClient: TexasClient,
) {
    private val log = KotlinLogging.logger {}

    suspend fun fetchSøker(fødselsnummer: String, subjectToken: String, callId: String): Result<SøkerRespons> {
        log.info { "fetchSøker: Henter token for å snakke med PDL" }
        val token = texasClient.exchangeToken(
            userToken = subjectToken,
            audienceTarget = pdlScope,
        )
        log.info { "fetchSøker: Token-exchange OK" }
        val pdlResponse: Result<SøkerRespons> = kotlin.runCatching {
            httpClient.post(pdlEndpoint) {
                accept(ContentType.Application.Json)
                header("Tema", INDIVIDSTONAD)
                header("Nav-Call-Id", callId)
                header("behandlingsnummer", "B470")
                bearerAuth(token.token)
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
        log.debug { "fetchAdressebeskyttelse:Token-respons mottatt" }
        val token = texasClient.exchangeToken(
            userToken = subjectToken,
            audienceTarget = pdlScope,
        )
        log.debug { "fetchAdressebeskyttelse: Token-exchange OK" }
        val pdlResponse: Result<AdressebeskyttelseRespons> = kotlin.runCatching {
            httpClient.post(pdlEndpoint) {
                accept(ContentType.Application.Json)
                header("Tema", INDIVIDSTONAD)
                header("Nav-Call-Id", callId)
                header("behandlingsnummer", "B470")
                bearerAuth(token.token)
                contentType(ContentType.Application.Json)
                setBody(hentAdressebeskyttelseQuery(fødselsnummer))
            }.body()
        }
        return pdlResponse
    }
}
