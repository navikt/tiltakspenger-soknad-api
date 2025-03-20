package no.nav.tiltakspenger.soknad.api.tiltak

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.TiltakDTO
import no.nav.tiltakspenger.soknad.api.auth.texas.client.TexasClient
import no.nav.tiltakspenger.soknad.api.httpClientWithRetry

class TiltakspengerTiltakClient(
    private val httpClient: HttpClient = httpClientWithRetry(timeout = 10L),
    private val tiltakspengerTiltakEndpoint: String,
    private val tiltakspengerTiltakScope: String,
    private val texasClient: TexasClient,
) {
    private val log = KotlinLogging.logger {}

    suspend fun fetchTiltak(subjectToken: String): Result<List<TiltakDTO>> {
        log.debug { "fetchTiltak: Henter token for å snakke med tiltakspenger-tiltak" }
        val token = texasClient.exchangeToken(
            userToken = subjectToken,
            audienceTarget = tiltakspengerTiltakScope,
        )
        log.debug { "fetchTiltak: Token til tiltakspenger-tiltak mottatt OK" }
        return kotlin.runCatching {
            httpClient.get("$tiltakspengerTiltakEndpoint/tokenx/tiltak") {
                accept(ContentType.Application.Json)
                bearerAuth(token.token)
                contentType(ContentType.Application.Json)
            }.body()
        }
    }
}
