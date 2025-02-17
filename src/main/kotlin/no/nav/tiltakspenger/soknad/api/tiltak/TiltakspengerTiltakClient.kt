package no.nav.tiltakspenger.soknad.api.tiltak

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.config.ApplicationConfig
import mu.KotlinLogging
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.TiltakDTO
import no.nav.tiltakspenger.soknad.api.auth.oauth.ClientConfig
import no.nav.tiltakspenger.soknad.api.extensions.getAccessTokenOrThrow
import no.nav.tiltakspenger.soknad.api.httpClientCIO
import no.nav.tiltakspenger.soknad.api.httpClientWithRetry

class TiltakspengerTiltakClient(
    config: ApplicationConfig,
    private val httpClient: HttpClient = httpClientWithRetry(timeout = 10L),
) {
    private val tiltakspengerTiltakEndpoint = config.property("endpoints.tiltakspengertiltak").getString()
    private val tiltakspengerTiltakAudience = config.property("audience.tiltakspengertiltak").getString()
    private val oauth2ClientTokenX = checkNotNull(ClientConfig(config, httpClientCIO()).clients["tokendings"])
    private val log = KotlinLogging.logger {}

    suspend fun fetchTiltak(subjectToken: String): Result<List<TiltakDTO>> {
        log.debug { "fetchTiltak: Henter token for å snakke med tiltakspenger-tiltak" }
        val tokenResponse = oauth2ClientTokenX.tokenExchange(subjectToken, tiltakspengerTiltakAudience)
        log.debug { "fetchTiltak:Token-respons mottatt" }
        val token = tokenResponse.getAccessTokenOrThrow()
        log.debug { "fetchTiltak: Token til tiltakspenger-tiltak mottatt OK" }
        return kotlin.runCatching {
            httpClient.get("$tiltakspengerTiltakEndpoint/tokenx/tiltak") {
                accept(ContentType.Application.Json)
                bearerAuth(token)
                contentType(ContentType.Application.Json)
            }.body()
        }
    }
}
