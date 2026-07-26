package no.nav.tiltakspenger.soknad.api.tiltak

import arrow.core.Either
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.retry.Retry
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import no.nav.tiltakspenger.libs.texas.IdentityProvider
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.libs.tiltak.TiltakshistorikkDTO
import java.net.URI
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Klient for å hente tiltaksdeltakelser fra tiltakspenger-tiltak.
 *
 * Kildekode: https://github.com/navikt/tiltakspenger-tiltak
 * Dokumentasjon: README-en i kildekode-repoet
 * API-spec: -
 * Slack: #tiltakspenger-værsågod (eget team)
 * Teamkatalog: https://teamkatalogen.nav.no/team/15bca3d2-2584-4167-85ba-faab1f1cfb53
 *
 * Kallet går alltid med brukerens eget token, vekslet til tiltakspenger-tiltak via TokenX, derfor [KlientAuth.Ingen] på klienten og bearer-token per kall.
 * Retryen replikerer den gamle ktor-klienten: fire forsøk totalt med konstant 100 ms delay.
 *
 * @param transport Det eneste stedet klienten rører nettverket; default er produksjonstransporten, tester sender inn `FakeHttpTransport`.
 */
class TiltakspengerTiltakClient(
    tiltakspengerTiltakEndpoint: String,
    clock: Clock,
    private val tiltakspengerTiltakScope: String,
    private val texasClient: TexasHttpClient,
    connectTimeout: Duration = 10.seconds,
    timeout: Duration = 10.seconds,
    transport: HttpTransport = JavaHttpTransport(connectTimeout = connectTimeout),
) {
    private val httpKlient: HttpKlient = HttpKlient(
        clock = clock,
        config = HttpKlientConfig(
            timeout = timeout,
            auth = KlientAuth.Ingen,
            retry = Retry.Fast(maksForsøk = 4, delay = 100.milliseconds),
        ),
        transport = transport,
    )

    private val tiltakshistorikkUri = URI.create("$tiltakspengerTiltakEndpoint/tokenx/tiltakshistorikk")

    private val cache: Cache<String, List<TiltakshistorikkDTO>> = Caffeine.newBuilder()
        .expireAfterWrite(java.time.Duration.ofMinutes(60))
        .build()

    suspend fun fetchTiltak(subjectToken: String, fnr: Fnr): Either<HttpKlientError, List<TiltakshistorikkDTO>> {
        cache.getIfPresent(fnr.verdi)?.let { return Either.Right(it) }
        val token = texasClient.exchangeToken(
            userToken = subjectToken,
            audienceTarget = tiltakspengerTiltakScope,
            identityProvider = IdentityProvider.TOKENX,
        )
        return httpKlient.getJson<List<TiltakshistorikkDTO>>(
            uri = tiltakshistorikkUri,
            bearerToken = token,
        ).map { it.body }.onRight { tiltak ->
            // Bare treff caches; et tomt svar skal kunne bli et treff senere uten å vente til cache-tiden løper ut.
            if (tiltak.isNotEmpty()) cache.put(fnr.verdi, tiltak)
        }
    }
}
