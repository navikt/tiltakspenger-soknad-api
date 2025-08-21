package no.nav.tiltakspenger.soknad.api.pdl

import arrow.core.Either
import arrow.core.getOrElse
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
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
import no.nav.tiltakspenger.libs.texas.IdentityProvider
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.soknad.api.httpClientWithRetry

const val INDIVIDSTONAD = "IND"

class PdlClientTokenX(
    private val httpClient: HttpClient = httpClientWithRetry(timeout = 10L),
    private val pdlEndpoint: String,
    private val pdlScope: String,
    private val texasClient: TexasHttpClient,
) {
    private val log = KotlinLogging.logger {}
    private val cache: Cache<String, AdressebeskyttelseRespons> = Caffeine.newBuilder()
        .expireAfterWrite(java.time.Duration.ofMinutes(60))
        .build()

    suspend fun fetchSøker(fødselsnummer: String, subjectToken: String, callId: String): SøkerRespons {
        log.info { "fetchSøker: Henter token for å snakke med PDL" }
        val token = texasClient.exchangeToken(
            userToken = subjectToken,
            audienceTarget = pdlScope,
            identityProvider = IdentityProvider.TOKENX,
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
            log.error(it) { "PdlClientTokenX(fetchSøker): Kall mot PDL feilet. Token-exchange var OK. Kallid: $callId." }
            throw it
        }
    }

    suspend fun fetchAdressebeskyttelse(
        fødselsnummer: String,
        subjectToken: String,
        callId: String,
    ): AdressebeskyttelseRespons {
        log.debug { "fetchAdressebeskyttelse:Token-respons mottatt" }
        cache.getIfPresent(fødselsnummer)?.let {
            log.debug { "Hentet adressebeskyttelse fra cache" }
            return it
        }
        val token = texasClient.exchangeToken(
            userToken = subjectToken,
            audienceTarget = pdlScope,
            identityProvider = IdentityProvider.TOKENX,
        )
        log.debug { "fetchAdressebeskyttelse: Token-exchange OK" }
        return Either.catch {
            val response = httpClient.post(pdlEndpoint) {
                accept(ContentType.Application.Json)
                header("Tema", INDIVIDSTONAD)
                header("Nav-Call-Id", callId)
                header("behandlingsnummer", "B470")
                bearerAuth(token.token)
                contentType(ContentType.Application.Json)
                setBody(hentAdressebeskyttelseQuery(fødselsnummer))
            }.body<AdressebeskyttelseRespons>()
            if (response.errors.isEmpty()) {
                cache.put(fødselsnummer, response)
            }
            response
        }.getOrElse {
            log.error(it) { "PdlClientTokenX(fetchAdressebeskyttelse): Kall mot PDL feilet. Token-exchange var OK. Kallid: $callId." }
            throw it
        }
    }
}
