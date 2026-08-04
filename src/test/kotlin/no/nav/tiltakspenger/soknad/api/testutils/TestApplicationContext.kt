package no.nav.tiltakspenger.soknad.api.testutils

import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.pdl.PdlIdentklient
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.TiltakshistorikkKlient
import no.nav.tiltakspenger.soknad.api.ApplicationContext
import no.nav.tiltakspenger.soknad.api.antivirus.ClamAvClient
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivClient
import no.nav.tiltakspenger.soknad.api.pdf.PdfClient
import no.nav.tiltakspenger.soknad.api.pdl.client.PdlClient
import no.nav.tiltakspenger.soknad.api.saksbehandlingApi.SaksbehandlingApiKlient
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakspengerTiltakClient
import no.nav.tiltakspenger.soknad.api.tiltak.skygge.TiltaksdeltakelseSkygge
import java.time.Clock

/**
 * Test-kontekst som bytter ut alt som rører nettverk og database, og lar resten av wiringen være ekte.
 * Klientene er produksjonsklientene med [FakeHttpTransport] som transport, så hele klient-pipelinen og servicene over dem kjører i testen.
 *
 * Transportene er FIFO-køer: legg inn ett svar per forventet kall før du gjør requesten.
 * Merk at [PdlClient] og [TiltakspengerTiltakClient] cacher på fødselsnummer i 60 minutter — bruk ulike fødselsnumre eller én kontekst per scenario når samme kall skal svare ulikt.
 */
open class TestApplicationContext(
    override val clock: Clock = fixedClock,
    override val søknadRepo: FakeSøknadRepo = FakeSøknadRepo(),
    /**
     * Styrer om [PdfClient] skygge-kaller pdfgenrs slik den gjør lokalt og i dev.
     * Default er `false`, altså prod-oppførselen, fordi skygge-kallet gjøres i parallell mot den samme FIFO-transporten.
     */
    isLocalOrDev: Boolean = false,
    /**
     * Skyggekjøringen mot tiltaksdeltakelse-modulen står av her, slik at tester som ikke handler om den slipper å kø svar for den.
     * Testene som øver den slår den på selv, og henter da svar fra [pdlIdentTransport] og [tiltakshistorikkTransport].
     */
    skyggePåslag: Boolean = false,
) : ApplicationContext(
    clock = clock,
    søknadRepo = søknadRepo,
    // Eget register per kontekst, slik at testene ikke deler global, muterende tilstand.
    collectorRegistry = CollectorRegistry(),
) {
    val pdlTransport = FakeHttpTransport()
    val tiltakTransport = FakeHttpTransport()
    val pdlIdentTransport = FakeHttpTransport()
    val tiltakshistorikkTransport = FakeHttpTransport()
    val clamAvTransport = FakeHttpTransport()
    val pdfTransport = FakeHttpTransport()
    val dokarkivTransport = FakeHttpTransport()
    val saksbehandlingApiTransport = FakeHttpTransport()

    override val texasClient = TexasClientFake(clock)

    override val personKlient = PdlClient(
        endepunkt = "http://pdl.test/graphql",
        clock = clock,
        pdlScope = "pdl-scope",
        texasClient = texasClient,
        authTokenProvider = TestTokenProvider(),
        transport = pdlTransport,
    )

    override val tiltakKlient = TiltakspengerTiltakClient(
        tiltakspengerTiltakEndpoint = "http://tiltak.test",
        clock = clock,
        tiltakspengerTiltakScope = "tiltak-scope",
        texasClient = texasClient,
        transport = tiltakTransport,
    )

    override val avKlient = ClamAvClient(
        avEndpoint = "http://clamav.test/scan",
        clock = clock,
        transport = clamAvTransport,
    )

    override val pdfGenerator = PdfClient(
        pdfEndpoint = "http://pdfgen.test",
        pdfgenrsEndpoint = "http://pdfgenrs.test",
        isLocalOrDev = isLocalOrDev,
        clock = clock,
        transport = pdfTransport,
    )

    override val journalpostKlient = DokarkivClient(
        baseUrl = "http://dokarkiv.test",
        clock = clock,
        authTokenProvider = TestTokenProvider(),
        transport = dokarkivTransport,
    )

    override val saksbehandlingKlient = SaksbehandlingApiKlient(
        baseUrl = "http://saksbehandling.test",
        clock = clock,
        authTokenProvider = TestTokenProvider(),
        transport = saksbehandlingApiTransport,
    )

    override val pdlIdentklient = PdlIdentklient(
        baseUrl = "http://pdl.test",
        clock = clock,
        authTokenProvider = TestTokenProvider(),
        transport = pdlIdentTransport,
    )

    override val tiltakshistorikkKlient = TiltakshistorikkKlient(
        baseUrl = "http://tiltakshistorikk.test",
        clock = clock,
        authTokenProvider = TestTokenProvider(),
        transport = tiltakshistorikkTransport,
    )

    /**
     * Samme form som i drift — [SupervisorJob] slik at ett mislykket sidekall ikke river med seg de andre — men på `Dispatchers.Unconfined`, så arbeidet skjer i tråden som startet det i stedet for på en trådpool.
     */
    override val skyggescope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /**
     * Venter til sidekallene skyggen har startet er ferdige.
     * I drift ventes det aldri på dem — det er hele poenget — så en rutetest må gjøre det her for å kunne si noe om dem i det hele tatt.
     */
    suspend fun ventPåSkyggen() {
        skyggescope.coroutineContext.job.children.toList().joinAll()
    }

    override val tiltaksdeltakelseSkygge = TiltaksdeltakelseSkygge(
        tiltakshistorikkHenter = tiltakshistorikkHenter,
        metricsCollector = metricsCollector,
        skyggescope = skyggescope,
        clock = clock,
        sikkerlogg = sikkerlogg,
        påslag = skyggePåslag,
    )
}
