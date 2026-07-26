package no.nav.tiltakspenger.soknad.api.saksbehandlingApi

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.kall.NavHeadere
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import no.nav.tiltakspenger.libs.httpklient.infra.retry.Retry
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import no.nav.tiltakspenger.libs.soknad.SøknadDTO
import java.net.URI
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Klient for å sende søknader og hente saksnummer fra tiltakspenger-saksbehandling-api.
 *
 * Kildekode: https://github.com/navikt/tiltakspenger-saksbehandling-api
 * Dokumentasjon: README-en i kildekode-repoet
 * API-spec: -
 * Slack: #tiltakspenger-værsågod (eget team)
 * Teamkatalog: https://teamkatalogen.nav.no/team/15bca3d2-2584-4167-85ba-faab1f1cfb53
 *
 * Retryen replikerer den gamle ktor-klienten: fire forsøk totalt med konstant 100 ms delay.
 * retryIkkeIdempotente er satt for paritet med den gamle klienten, som retryet begge POST-kallene; begge er reelt idempotente (hent-eller-opprett, og søknadsmottaket dedupliserer på søknadId).
 *
 * @param transport Det eneste stedet klienten rører nettverket; default er produksjonstransporten, tester sender inn `FakeHttpTransport`.
 */
class SaksbehandlingApiKlient(
    baseUrl: String,
    clock: Clock,
    authTokenProvider: AuthTokenProvider,
    connectTimeout: Duration = 10.seconds,
    timeout: Duration = 10.seconds,
    transport: HttpTransport = JavaHttpTransport(connectTimeout = connectTimeout),
) : SaksbehandlingKlient {
    private val httpKlient: HttpKlient = HttpKlient(
        clock = clock,
        config = HttpKlientConfig(
            timeout = timeout,
            auth = KlientAuth.System(authTokenProvider),
            retry = Retry.Fast(maksForsøk = 4, delay = 100.milliseconds, retryIkkeIdempotente = true),
        ),
        transport = transport,
    )

    private val søknadUri = URI.create("$baseUrl/soknad")
    private val saksnummerUri = URI.create("$baseUrl/saksnummer")

    override suspend fun sendSøknad(
        søknadDTO: SøknadDTO,
        correlationId: CorrelationId,
    ): Either<HttpKlientError, Unit> =
        httpKlient.postJsonUtenSvar(
            uri = søknadUri,
            body = søknadDTO,
            headere = listOf(NavHeadere.navCallId(correlationId.toString())),
            godta = Statusregel.Eksakt(200),
        ).map { }

    override suspend fun hentEllerOpprettSaksnummer(
        fnr: Fnr,
        correlationId: CorrelationId,
    ): Either<HttpKlientError, String> =
        httpKlient.postJson<SaksnummerResponse>(
            uri = saksnummerUri,
            body = FnrDTO(fnr.verdi),
            headere = listOf(NavHeadere.navCallId(correlationId.toString())),
            godta = Statusregel.Eksakt(200),
        ).map { it.body.saksnummer }
}

data class FnrDTO(
    val fnr: String,
) {
    /** [fnr] er PII og skal ikke bli med om noen logger hele objektet. */
    override fun toString() = "FnrDTO(fnr=*****)"
}

data class SaksnummerResponse(
    val saksnummer: String,
)
