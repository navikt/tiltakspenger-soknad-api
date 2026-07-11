package no.nav.tiltakspenger.soknad.api.tiltak

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.texas.IdentityProvider
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.libs.tiltak.TiltakshistorikkDTO
import no.nav.tiltakspenger.soknad.api.httpClientWithRetry
import java.time.Duration

/**
 * Klient for å hente tiltaksdeltakelser fra tiltakspenger-tiltak.
 *
 * Kildekode: https://github.com/navikt/tiltakspenger-tiltak
 * Dokumentasjon: README-en i kildekode-repoet
 * API-spec: -
 * Slack: #tiltakspenger-værsågod (eget team)
 * Teamkatalog: https://teamkatalogen.nav.no/team/15bca3d2-2584-4167-85ba-faab1f1cfb53
 */
class TiltakspengerTiltakClient(
    private val httpClient: HttpClient = httpClientWithRetry(timeout = 10L),
    private val tiltakspengerTiltakEndpoint: String,
    private val tiltakspengerTiltakScope: String,
    private val texasClient: TexasHttpClient,
) {
    private val log = KotlinLogging.logger {}
    private val cache: Cache<String, List<TiltakshistorikkDTO>> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(60))
        .build()

    suspend fun fetchTiltak(subjectToken: String, fnr: Fnr): Result<List<TiltakshistorikkDTO>> {
        cache.getIfPresent(fnr.verdi)?.let {
            log.info { "Hentet tiltaksdeltakelser fra cache" }
            return Result.success(it)
        }
        val token = texasClient.exchangeToken(
            userToken = subjectToken,
            audienceTarget = tiltakspengerTiltakScope,
            identityProvider = IdentityProvider.TOKENX,
        )
        log.info { "Henter tiltaksdeltakelser fra tiltakspenger-tiltak" }
        val result: Result<List<TiltakshistorikkDTO>> = kotlin.runCatching {
            httpClient.get("$tiltakspengerTiltakEndpoint/tokenx/tiltakshistorikk") {
                accept(ContentType.Application.Json)
                bearerAuth(token.token)
                contentType(ContentType.Application.Json)
            }.body()
        }
        if (result.isSuccess && !result.getOrNull().isNullOrEmpty()) {
            cache.put(fnr.verdi, result.getOrNull()!!)
        }
        return result
    }
}
