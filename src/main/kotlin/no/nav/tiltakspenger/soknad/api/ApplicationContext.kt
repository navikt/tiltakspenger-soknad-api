package no.nav.tiltakspenger.soknad.api

import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.libs.logging.infra.KotlinLoggingSikkerlogg
import no.nav.tiltakspenger.libs.texas.client.TexasClient
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.libs.texas.client.TexasSystemTokenProvider
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.pdl.PdlIdentklient
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.TiltakshistorikkHenter
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.TiltakshistorikkKlient
import no.nav.tiltakspenger.soknad.api.antivirus.AvKlient
import no.nav.tiltakspenger.soknad.api.antivirus.AvService
import no.nav.tiltakspenger.soknad.api.antivirus.ClamAvClient
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivClient
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivService
import no.nav.tiltakspenger.soknad.api.dokarkiv.JournalpostKlient
import no.nav.tiltakspenger.soknad.api.identhendelse.IdenthendelseConsumer
import no.nav.tiltakspenger.soknad.api.identhendelse.IdenthendelseService
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import no.nav.tiltakspenger.soknad.api.pdf.PdfClient
import no.nav.tiltakspenger.soknad.api.pdf.PdfGenerator
import no.nav.tiltakspenger.soknad.api.pdf.PdfService
import no.nav.tiltakspenger.soknad.api.pdf.PdfServiceImpl
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.pdl.client.PdlClient
import no.nav.tiltakspenger.soknad.api.pdl.client.PersonKlient
import no.nav.tiltakspenger.soknad.api.saksbehandlingApi.SaksbehandlingApiKlient
import no.nav.tiltakspenger.soknad.api.saksbehandlingApi.SaksbehandlingKlient
import no.nav.tiltakspenger.soknad.api.soknad.NySøknadService
import no.nav.tiltakspenger.soknad.api.soknad.SøknadRepo
import no.nav.tiltakspenger.soknad.api.soknad.jobb.SøknadJobbService
import no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforing.JournalforingService
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakKlient
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakService
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakspengerTiltakClient
import no.nav.tiltakspenger.soknad.api.tiltak.skygge.TiltaksdeltakelseSkygge
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
    fun systemTokenProvider(scope: String) = TexasSystemTokenProvider(
        texasClient = texasClient,
        audienceTarget = scope,
    )

    open val personKlient: PersonKlient by lazy {
        PdlClient(
            endepunkt = Configuration.pdlUrl,
            clock = clock,
            pdlScope = Configuration.pdlScope,
            texasClient = texasClient,
            authTokenProvider = systemTokenProvider(Configuration.pdlScope),
        )
    }

    open val tiltakKlient: TiltakKlient by lazy {
        TiltakspengerTiltakClient(
            tiltakspengerTiltakEndpoint = Configuration.tiltakspengerTiltakUrl,
            clock = clock,
            tiltakspengerTiltakScope = Configuration.tiltakspengerTiltakScope,
            texasClient = texasClient,
        )
    }

    /**
     * De tre neste hører til skyggekjøringen av tiltaksdeltakelse-modulen, som skal overta for tiltakspenger-tiltak, og dør med den.
     * Begge klientene går med systemtoken, slik dagens kjede om tiltakspenger-tiltak også gjør — brukertokenet autentiserer bare inn til oss.
     */
    open val pdlIdentklient: PdlIdentklient by lazy {
        PdlIdentklient(
            baseUrl = Configuration.pdlBaseUrl,
            clock = clock,
            authTokenProvider = systemTokenProvider(Configuration.pdlScope),
        )
    }

    open val tiltakshistorikkKlient: TiltakshistorikkKlient by lazy {
        TiltakshistorikkKlient(
            baseUrl = Configuration.tiltakshistorikkUrl,
            clock = clock,
            authTokenProvider = systemTokenProvider(Configuration.tiltakshistorikkScope),
        )
    }

    open val tiltakshistorikkHenter: TiltakshistorikkHenter by lazy {
        TiltakshistorikkHenter(
            tiltakshistorikkKlient = tiltakshistorikkKlient,
            pdlIdentklient = pdlIdentklient,
            clock = clock,
        )
    }

    /**
     * Scopet skyggekjøringen lever i, utenfor request-pathen.
     * [SupervisorJob] gjør at én mislykket sammenligning ikke river med seg de andre, og IO-dispatcheren brukes fordi arbeidet er to nettverkskall.
     */
    open val skyggescope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    /**
     * Skyggen kjører i både dev og prod.
     *
     * Prod ble tatt med fordi dev ikke har trafikk: `/tiltak` ble ikke kalt én gang der på et døgn, så empirien vi er ute etter — hvilke avviksklasser som faktisk opptrer, og hvor ofte — finnes bare i prod.
     * Prisen er ett ekstra oppslag mot PDL og tiltakshistorikk per ferske henting, som er den dobbeltlasten Team Valp ble varslet om da vi ba om tilgang.
     * Den er tidsbegrenset: skyggen slås av ved cutover, og fram til da kalles ny vei aldri på cachede svar.
     *
     * Den står av lokalt fordi lokalmiljøet ikke har noen tiltakshistorikk å svare med, og en skygge som bare feiler er støy.
     * Dette er én linje som endres og deployes; en bryter utenfor koden kjøper ingenting når vi uansett må deploye.
     *
     * Hele kjeden fra ruta og ut til begge kildene kjøres i test uavhengig av dette — se `TiltaksdeltakelseSkyggeRouteTest`, som slår den på i test-konteksten.
     */
    open val tiltaksdeltakelseSkygge: TiltaksdeltakelseSkygge by lazy {
        TiltaksdeltakelseSkygge(
            tiltakshistorikkHenter = tiltakshistorikkHenter,
            metricsCollector = metricsCollector,
            skyggescope = skyggescope,
            clock = clock,
            sikkerlogg = sikkerlogg,
            påslag = Configuration.isNais(),
        )
    }

    open val avKlient: AvKlient by lazy {
        ClamAvClient(
            avEndpoint = Configuration.avUrl,
            clock = clock,
        )
    }

    open val pdfGenerator: PdfGenerator by lazy {
        PdfClient(
            pdfEndpoint = Configuration.pdfUrl,
            pdfgenrsEndpoint = Configuration.pdfgenrsUrl,
            isLocalOrDev = Configuration.isLocalOrDev(),
            clock = clock,
        )
    }

    open val journalpostKlient: JournalpostKlient by lazy {
        DokarkivClient(
            baseUrl = Configuration.dokarkivUrl,
            clock = clock,
            authTokenProvider = systemTokenProvider(Configuration.dokarkivScope),
        )
    }

    open val saksbehandlingKlient: SaksbehandlingKlient by lazy {
        SaksbehandlingApiKlient(
            baseUrl = Configuration.saksbehandlingApiUrl,
            clock = clock,
            authTokenProvider = systemTokenProvider(Configuration.saksbehandlingApiScope),
        )
    }

    open val pdfService: PdfService by lazy { PdfServiceImpl(pdfGenerator) }
    open val dokarkivService: DokarkivService by lazy { DokarkivService(journalpostKlient) }
    open val journalforingService: JournalforingService by lazy {
        JournalforingService(
            pdfService = pdfService,
            dokarkivService = dokarkivService,
            sikkerlogg = sikkerlogg,
        )
    }

    open val pdlService: PdlService by lazy { PdlService(personKlient, clock, sikkerlogg) }
    open val tiltakService: TiltakService by lazy { TiltakService(tiltakKlient, clock, sikkerlogg, tiltaksdeltakelseSkygge) }
    open val avService: AvService by lazy { AvService(avKlient, sikkerlogg) }
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
            saksbehandlingKlient = saksbehandlingKlient,
            clock = clock,
            sikkerlogg = sikkerlogg,
        )
    }
}
