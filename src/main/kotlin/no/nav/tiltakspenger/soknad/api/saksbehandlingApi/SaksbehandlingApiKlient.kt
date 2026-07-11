package no.nav.tiltakspenger.soknad.api.saksbehandlingApi

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.soknad.SøknadDTO
import no.nav.tiltakspenger.soknad.api.httpClientWithRetry

/**
 * Klient for å sende søknader og hente saksnummer fra tiltakspenger-saksbehandling-api.
 *
 * Kildekode: https://github.com/navikt/tiltakspenger-saksbehandling-api
 * Dokumentasjon: README-en i kildekode-repoet
 * API-spec: -
 * Slack: #tiltakspenger-værsågod (eget team)
 * Teamkatalog: https://teamkatalogen.nav.no/team/15bca3d2-2584-4167-85ba-faab1f1cfb53
 */
class SaksbehandlingApiKlient(
    private val httpClient: HttpClient = httpClientWithRetry(timeout = 10L),
    private val baseUrl: String,
    private val getToken: suspend () -> AccessToken,
) {
    suspend fun sendSøknad(søknadDTO: SøknadDTO, correlationId: CorrelationId) {
        val httpResponse = httpClient.preparePost("$baseUrl/soknad") {
            header("Nav-Call-Id", correlationId.toString())
            bearerAuth(getToken().token)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(søknadDTO)
        }.execute()
        when (httpResponse.status) {
            HttpStatusCode.OK -> return
            else -> throw RuntimeException("error (responseCode=${httpResponse.status.value}) from saksbehandling-api")
        }
    }

    suspend fun hentEllerOpprettSaksnummer(fnr: Fnr, correlationId: CorrelationId): String {
        val httpResponse = httpClient.preparePost("$baseUrl/saksnummer") {
            header("Nav-Call-Id", correlationId.toString())
            bearerAuth(getToken().token)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(FnrDTO(fnr.verdi))
        }.execute()
        when (httpResponse.status) {
            HttpStatusCode.OK -> return httpResponse.body<SaksnummerResponse>().saksnummer
            else -> throw RuntimeException("saksbehandling-api svarte med feilkode ved henting av saksnummer: ${httpResponse.status.value}")
        }
    }
}

data class FnrDTO(
    val fnr: String,
)

data class SaksnummerResponse(
    val saksnummer: String,
)
