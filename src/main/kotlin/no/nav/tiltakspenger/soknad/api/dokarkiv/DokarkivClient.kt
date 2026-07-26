package no.nav.tiltakspenger.soknad.api.dokarkiv

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.recover
import arrow.core.right
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.harStatus
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.feil.bodySomJson
import no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.kall.NavHeadere
import no.nav.tiltakspenger.libs.httpklient.infra.retry.Retry
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import java.net.URI
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal const val DOKARKIV_PATH = "rest/journalpostapi/v1/journalpost"

/**
 * Klient for å opprette journalposter i dokarkiv (Joark).
 *
 * Kildekode: https://github.com/navikt/dokarkiv
 * Dokumentasjon: https://confluence.adeo.no/display/BOA/dokarkiv og https://confluence.adeo.no/display/BOA/opprettJournalpost
 * API-spec: https://dokarkiv.dev.intern.nav.no/swagger-ui/index.html
 * Slack: #team-dokumentløsninger (https://nav-it.slack.com/archives/C6W9E5GPJ)
 * Teamkatalog: https://teamkatalogen.nav.no/team/f3388fcd-898e-40da-8d02-0bf1e3a79120
 *
 * Dokarkiv dedupliserer på `eksternReferanseId` og svarer `409 Conflict` med journalpost-IDen fra det opprinnelige kallet.
 * `409` er derfor et domeneutfall og ikke en suksess-status: den utledes fra feiltypen, slik at suksess-kanalen beholder én betydning.
 * Retryen replikerer den gamle ktor-klienten: fire forsøk totalt med konstant 100 ms delay, og retryIkkeIdempotente er trygt nettopp på grunn av dedupliseringen.
 *
 * @param transport Det eneste stedet klienten rører nettverket; default er produksjonstransporten, tester sender inn `FakeHttpTransport`.
 */
class DokarkivClient(
    baseUrl: String,
    clock: Clock,
    authTokenProvider: AuthTokenProvider,
    connectTimeout: Duration = 30.seconds,
    timeout: Duration = 30.seconds,
    transport: HttpTransport = JavaHttpTransport(connectTimeout = connectTimeout),
) {
    private val httpKlient: HttpKlient = HttpKlient(
        clock = clock,
        config = HttpKlientConfig(
            timeout = timeout,
            auth = KlientAuth.System(authTokenProvider),
            retry = Retry.Fast(maksForsøk = 4, delay = 100.milliseconds, retryIkkeIdempotente = true),
        ),
        transport = transport,
    )

    private val journalpostUri = URI.create("$baseUrl/$DOKARKIV_PATH")

    suspend fun opprettJournalpost(
        request: JournalpostRequest,
        callId: String,
    ): Either<KunneIkkeJournalføre, JournalpostId> {
        val skalFerdigstilles = request.kanFerdigstilleAutomatisk()
        return httpKlient.postJson<DokarkivResponse>(
            uri = URI.create("$journalpostUri?forsoekFerdigstill=$skalFerdigstilles"),
            body = request,
            headere = listOf(NavHeadere.xCorrelationId(callId), NavHeadere.navCallid(callId)),
        ).map { it.body }
            .recover { feil -> feil.tilDedupliseringsrespons().bind() }
            .flatMap { it.tilJournalpostId(skalFerdigstilles) }
    }

    /**
     * `409 Conflict` betyr at journalposten allerede finnes; dokarkiv svarer da med den opprinnelige journalpost-IDen i samme format som ved `200`.
     * Klarer vi ikke å lese den bodyen, er dedupliseringen ubrukelig for oss, og feilen forblir en feil.
     */
    private fun HttpKlientError.tilDedupliseringsrespons(): Either<KunneIkkeJournalføre, DokarkivResponse> =
        if (harStatus(409)) {
            (this as HttpKlientError.ResponsMottatt).bodySomJson<DokarkivResponse>()
                .mapLeft { KunneIkkeJournalføre.KallFeilet(it) }
        } else {
            KunneIkkeJournalføre.KallFeilet(this).left()
        }

    private fun DokarkivResponse.tilJournalpostId(skalFerdigstilles: Boolean): Either<KunneIkkeJournalføre, JournalpostId> =
        if (skalFerdigstilles && !journalpostferdigstilt) {
            KunneIkkeJournalføre.IkkeFerdigstilt(JournalpostId(journalpostId)).left()
        } else {
            JournalpostId(journalpostId).right()
        }
}

data class DokarkivResponse(
    val journalpostId: String,
    val journalpostferdigstilt: Boolean,
)
