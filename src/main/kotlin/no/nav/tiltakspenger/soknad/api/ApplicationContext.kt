package no.nav.tiltakspenger.soknad.api

import io.prometheus.client.CollectorRegistry
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.libs.logging.infra.KotlinLoggingSikkerlogg
import no.nav.tiltakspenger.libs.texas.client.TexasClient
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.libs.texas.client.TexasSystemTokenProvider
import no.nav.tiltakspenger.soknad.api.antivirus.AvService
import no.nav.tiltakspenger.soknad.api.antivirus.ClamAvClient
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivClient
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivService
import no.nav.tiltakspenger.soknad.api.identhendelse.IdenthendelseConsumer
import no.nav.tiltakspenger.soknad.api.identhendelse.IdenthendelseService
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import no.nav.tiltakspenger.soknad.api.pdf.PdfClient
import no.nav.tiltakspenger.soknad.api.pdf.PdfService
import no.nav.tiltakspenger.soknad.api.pdf.PdfServiceImpl
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.pdl.client.PdlClient
import no.nav.tiltakspenger.soknad.api.saksbehandlingApi.SaksbehandlingApiKlient
import no.nav.tiltakspenger.soknad.api.soknad.NySøknadService
import no.nav.tiltakspenger.soknad.api.soknad.SøknadRepo
import no.nav.tiltakspenger.soknad.api.soknad.jobb.SøknadJobbService
import no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforing.JournalforingService
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakService
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakspengerTiltakClient
import java.time.Clock

/**
 * Wiringen av applikasjonen, etter samme mønster som de øvrige appene i flåten.
 * Alt bygges `by lazy` og er `open`, slik at en test-kontekst kan overstyre enkeltavhengigheter med fakes uten at basen rekker å bygge produksjonsvarianten først.
 *
 * Konteksten leser [Configuration] selv, men gjør ingen I/O ved konstruksjon.
 * Derfor tas datasource-avhengigheten inn ferdig som [søknadRepo] — [no.nav.tiltakspenger.soknad.api.db.DataSourceSetup] kobler til ved konstruksjon, og en kontekst som gjorde det kunne ikke vært bygget i en test.
 * Av samme grunn tas [CollectorRegistry] inn: drift sender inn det globale default-registeret, tester sitt eget.
 */
open class ApplicationContext(
    open val clock: Clock,
    open val søknadRepo: SøknadRepo,
    private val collectorRegistry: CollectorRegistry,
) {
    open val metricsCollector: MetricsCollector by lazy { MetricsCollector(collectorRegistry) }

    open val sikkerlogg: Sikkerlogg by lazy {
        KotlinLoggingSikkerlogg(
            appNavn = Configuration.naisAppName,
            gcpProsjektId = Configuration.gcpTeamProjectId,
        )
    }

    open val texasClient: TexasClient by lazy {
        TexasHttpClient(
            introspectionUrl = Configuration.naisTokenIntrospectionEndpoint,
            tokenUrl = Configuration.naisTokenEndpoint,
            tokenExchangeUrl = Configuration.tokenExchangeEndpoint,
            clock = clock,
        )
    }

    /**
     * Scope-verdiene våre kommer fra nais-manifestet på formen `prod-fss:pdl:pdl-api`, mens Azure AD krever `api://prod-fss.pdl.pdl-api/.default`.
     * Omskrivingen må derfor stå på (default i [TexasSystemTokenProvider]) — skrur man den av, svarer Azure AD `invalid_scope` (AADSTS1002012) på alle systemtokens.
     * Appene som sender inn ferdige `api://…/.default`-scopes gjør det motsatte valget; her er det den korte formen som gjelder.
     */
    internal fun systemTokenProvider(scope: String) = TexasSystemTokenProvider(
        texasClient = texasClient,
        audienceTarget = scope,
    )

    open val pdlClient: PdlClient by lazy {
        PdlClient(
            endepunkt = Configuration.pdlUrl,
            clock = clock,
            pdlScope = Configuration.pdlScope,
            texasClient = texasClient,
            authTokenProvider = systemTokenProvider(Configuration.pdlScope),
        )
    }

    open val tiltakspengerTiltakClient: TiltakspengerTiltakClient by lazy {
        TiltakspengerTiltakClient(
            tiltakspengerTiltakEndpoint = Configuration.tiltakspengerTiltakUrl,
            clock = clock,
            tiltakspengerTiltakScope = Configuration.tiltakspengerTiltakScope,
            texasClient = texasClient,
        )
    }

    open val clamAvClient: ClamAvClient by lazy {
        ClamAvClient(
            avEndpoint = Configuration.avUrl,
            clock = clock,
        )
    }

    open val pdfClient: PdfClient by lazy {
        PdfClient(
            pdfEndpoint = Configuration.pdfUrl,
            pdfgenrsEndpoint = Configuration.pdfgenrsUrl,
            isLocalOrDev = Configuration.isLocalOrDev(),
            clock = clock,
        )
    }

    open val dokarkivClient: DokarkivClient by lazy {
        DokarkivClient(
            baseUrl = Configuration.dokarkivUrl,
            clock = clock,
            authTokenProvider = systemTokenProvider(Configuration.dokarkivScope),
        )
    }

    open val saksbehandlingApiKlient: SaksbehandlingApiKlient by lazy {
        SaksbehandlingApiKlient(
            baseUrl = Configuration.saksbehandlingApiUrl,
            clock = clock,
            authTokenProvider = systemTokenProvider(Configuration.saksbehandlingApiScope),
        )
    }

    open val pdfService: PdfService by lazy { PdfServiceImpl(pdfClient) }
    open val dokarkivService: DokarkivService by lazy { DokarkivService(dokarkivClient) }
    open val journalforingService: JournalforingService by lazy {
        JournalforingService(
            pdfService = pdfService,
            dokarkivService = dokarkivService,
            sikkerlogg = sikkerlogg,
        )
    }

    open val pdlService: PdlService by lazy { PdlService(pdlClient, clock, sikkerlogg) }
    open val tiltakService: TiltakService by lazy { TiltakService(tiltakspengerTiltakClient, clock, sikkerlogg) }
    open val avService: AvService by lazy { AvService(clamAvClient, sikkerlogg) }
    open val nySøknadService: NySøknadService by lazy { NySøknadService(søknadRepo) }
    open val identhendelseService: IdenthendelseService by lazy { IdenthendelseService(søknadRepo) }
    open val identhendelseConsumer: IdenthendelseConsumer by lazy {
        IdenthendelseConsumer(
            identhendelseService = identhendelseService,
            topic = Configuration.identhendelseTopic,
        )
    }

    open val søknadJobbService: SøknadJobbService by lazy {
        SøknadJobbService(
            søknadRepo = søknadRepo,
            pdlService = pdlService,
            journalforingService = journalforingService,
            saksbehandlingApiKlient = saksbehandlingApiKlient,
            clock = clock,
            sikkerlogg = sikkerlogg,
        )
    }
}
