package no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforendeEnhet.arbeidsfordeling

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import mu.KotlinLogging
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.soknad.api.httpClientWithRetry
import no.nav.tiltakspenger.soknad.api.objectMapper

class ArbeidsfordelingClient(
    private val httpClient: HttpClient = httpClientWithRetry(timeout = 5L),
    private val baseUrl: String,
    private val getToken: suspend () -> AccessToken,
) {
    private val log = KotlinLogging.logger {}

    suspend fun hentArbeidsfordeling(request: ArbeidsfordelingRequest): String {
        val response = httpClient.post("$baseUrl/api/v1/arbeidsfordeling/enheter/bestmatch") {
            accept(ContentType.Application.Json)
            bearerAuth(getToken().token)
            header("Nav-Consumer-Id", "tiltakspenger-soknad-api")
            contentType(ContentType.Application.Json)
            setBody(objectMapper.writeValueAsString(request))
        }
        if (response.status.isSuccess()) {
            val enheter = response.body<List<NavEnhet>>()
            if (enheter.isEmpty()) {
                log.error("Fant ingen enheter")
                throw IllegalArgumentException("Fant ingen enheter")
            }
            return enheter.first().enhetNr
        }
        log.error("Kall mot norg2 for å hente arbeidsfordeling feilet med statuskode ${response.status}")
        throw RuntimeException("Kunne ikke hente arbeidsfordeling")
    }
}

data class ArbeidsfordelingRequest(
    val diskresjonskode: String?,
    val oppgavetype: String = "BEH_SAK",
    val tema: String = "IND",
    val geografiskOmraade: String?,
    val skjermet: Boolean = false,
)

private data class NavEnhet(
    val enhetNr: String,
)
