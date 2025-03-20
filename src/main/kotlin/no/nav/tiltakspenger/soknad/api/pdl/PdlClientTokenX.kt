package no.nav.tiltakspenger.soknad.api.pdl

import arrow.core.Either
import arrow.core.getOrElse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.tiltakspenger.libs.logging.sikkerlogg
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

    suspend fun fetchSøker(fødselsnummer: String, subjectToken: String, callId: String): SøkerRespons {
        log.info { "fetchSøker: Henter token for å snakke med PDL" }
        val token = texasClient.exchangeToken(
            userToken = subjectToken,
            audienceTarget = pdlScope,
        )
        log.info { "fetchSøker: Token-exchange OK" }
        return Either.catch {
            httpClient.post(pdlEndpoint) {
                accept(ContentType.Application.Json)
                header("Tema", INDIVIDSTONAD)
                header("Nav-Call-Id", callId)
                header("behandlingsnummer", "B470")
                bearerAuth(token.token)
                contentType(ContentType.Application.Json)
                setBody(hentPersonQuery(fødselsnummer))
            }.body<SøkerRespons>()
        }.getOrElse {
            log.error(RuntimeException("Trigger stacktrace for enklere debug.")) { "PdlClientTokenX(fetchSøker): Kall mot PDL feilet. Token-exchange var OK. Kallid: $callId. Se sikkerlogg for mer kontekst." }
            sikkerlogg.error(it) { "PdlClientTokenX(fetchSøker): Kall mot PDL feilet. Token-exchange var OK. Kallid: $callId." }
            throw it
        }
    }

    suspend fun fetchAdressebeskyttelse(
        fødselsnummer: String,
        subjectToken: String,
        callId: String,
    ): AdressebeskyttelseRespons {
        log.debug { "fetchAdressebeskyttelse:Token-respons mottatt" }
        val token = texasClient.exchangeToken(
            userToken = subjectToken,
            audienceTarget = pdlScope,
        )
        log.debug { "fetchAdressebeskyttelse: Token-exchange OK" }
        return Either.catch {
            httpClient.post(pdlEndpoint) {
                accept(ContentType.Application.Json)
                header("Tema", INDIVIDSTONAD)
                header("Nav-Call-Id", callId)
                header("behandlingsnummer", "B470")
                bearerAuth(token.token)
                contentType(ContentType.Application.Json)
                setBody(hentAdressebeskyttelseQuery(fødselsnummer))
            }.body<AdressebeskyttelseRespons>()
        }.getOrElse {
            log.error(RuntimeException("Trigger stacktrace for enklere debug.")) { "PdlClientTokenX(fetchAdressebeskyttelse): Kall mot PDL feilet. Token-exchange var OK. Kallid: $callId. Se sikkerlogg for mer kontekst." }
            sikkerlogg.error(it) { "PdlClientTokenX(fetchAdressebeskyttelse): Kall mot PDL feilet. Token-exchange var OK. Kallid: $callId." }
            throw it
        }
    }
}
