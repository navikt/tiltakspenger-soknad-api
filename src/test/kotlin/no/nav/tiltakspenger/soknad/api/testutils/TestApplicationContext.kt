package no.nav.tiltakspenger.soknad.api.testutils

import io.prometheus.client.CollectorRegistry
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
import java.time.Clock

/**
 * Test-kontekst som bytter ut alt som rører nettverk og database, og lar resten av wiringen være ekte.
 * Klientene er produksjonsklientene med [FakeHttpTransport] som transport, så hele klient-pipelinen og servicene over dem kjører i testen.
 *
 * Transportene er FIFO-køer: legg inn ett svar per forventet kall før du gjør requesten.
 * Merk at [PdlClient] cacher søkeroppslaget på fødselsnummer i 60 minutter — bruk ulike fødselsnumre eller én kontekst per scenario når samme kall skal svare ulikt.
 *
 * Hentingen av tiltaksdeltakelser gjør to kall per request, ett mot hver av [pdlIdentTransport] og [tiltakshistorikkTransport], og den har ingen cache.
 * To requester i samme test krever derfor to svar i hver av dem.
 */
open class TestApplicationContext(
    override val clock: Clock = fixedClock,
    override val søknadRepo: FakeSøknadRepo = FakeSøknadRepo(),
    /**
     * Styrer om [PdfClient] skygge-kaller pdfgenrs slik den gjør lokalt og i dev.
     * Default er `false`, altså prod-oppførselen, fordi skygge-kallet gjøres i parallell mot den samme FIFO-transporten.
     */
    isLocalOrDev: Boolean = false,
) : ApplicationContext(
    clock = clock,
    søknadRepo = søknadRepo,
    // Eget register per kontekst, slik at testene ikke deler global, muterende tilstand.
    collectorRegistry = CollectorRegistry(),
) {
    val pdlTransport = FakeHttpTransport()
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

    override val avKlient = ClamAvClient(
        avEndpoint = "http://clamav.test/scan",
        clock = clock,
        transport = clamAvTransport,
    )

    override val pdfGenerator = PdfClient(
        pdfgenrsEndpoint = "http://pdfgenrs.test",
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
}
