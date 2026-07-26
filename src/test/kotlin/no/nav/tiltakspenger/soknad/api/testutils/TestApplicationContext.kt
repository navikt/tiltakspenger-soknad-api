package no.nav.tiltakspenger.soknad.api.testutils

import io.prometheus.client.CollectorRegistry
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.soknad.api.ApplicationContext
import no.nav.tiltakspenger.soknad.api.antivirus.ClamAvClient
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivClient
import no.nav.tiltakspenger.soknad.api.pdf.PdfClient
import no.nav.tiltakspenger.soknad.api.pdl.client.PdlClient
import no.nav.tiltakspenger.soknad.api.saksbehandlingApi.SaksbehandlingApiKlient
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakspengerTiltakClient
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
     * Settes når alle klientene skal dele én rutende transport i stedet for hver sin kø.
     * Brukes til å verifisere [LokalHttpTransport], som er det lokal kjøring står på.
     */
    private val fellesTransport: HttpTransport? = null,
) : ApplicationContext(
    clock = clock,
    søknadRepo = søknadRepo,
    // Eget register per kontekst, slik at testene ikke deler global, muterende tilstand.
    collectorRegistry = CollectorRegistry(),
) {
    val pdlTransport = FakeHttpTransport()
    val tiltakTransport = FakeHttpTransport()
    val clamAvTransport = FakeHttpTransport()
    val pdfTransport = FakeHttpTransport()
    val dokarkivTransport = FakeHttpTransport()
    val saksbehandlingApiTransport = FakeHttpTransport()

    override val texasClient = TexasClientFake(clock)

    override val pdlClient = PdlClient(
        endepunkt = "http://pdl.test/graphql",
        clock = clock,
        pdlScope = "pdl-scope",
        texasClient = texasClient,
        authTokenProvider = TestTokenProvider(),
        transport = fellesTransport ?: pdlTransport,
    )

    override val tiltakspengerTiltakClient = TiltakspengerTiltakClient(
        tiltakspengerTiltakEndpoint = "http://tiltak.test",
        clock = clock,
        tiltakspengerTiltakScope = "tiltak-scope",
        texasClient = texasClient,
        transport = fellesTransport ?: tiltakTransport,
    )

    override val clamAvClient = ClamAvClient(
        avEndpoint = "http://clamav.test/scan",
        clock = clock,
        transport = fellesTransport ?: clamAvTransport,
    )

    override val pdfClient = PdfClient(
        pdfEndpoint = "http://pdfgen.test",
        pdfgenrsEndpoint = "http://pdfgenrs.test",
        isLocalOrDev = isLocalOrDev,
        clock = clock,
        transport = fellesTransport ?: pdfTransport,
    )

    override val dokarkivClient = DokarkivClient(
        baseUrl = "http://dokarkiv.test",
        clock = clock,
        authTokenProvider = TestTokenProvider(),
        transport = fellesTransport ?: dokarkivTransport,
    )

    override val saksbehandlingApiKlient = SaksbehandlingApiKlient(
        baseUrl = "http://saksbehandling.test",
        clock = clock,
        authTokenProvider = TestTokenProvider(),
        transport = fellesTransport ?: saksbehandlingApiTransport,
    )
}
