package no.nav.tiltakspenger.soknad.api

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.hotspot.DefaultExports
import no.nav.tiltakspenger.libs.jobber.TaskResultat
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Bakgrunnsprosessoppsett
import no.nav.tiltakspenger.libs.ktor.common.oppstart.KafkaConsumerOppsett
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Miljøverdi
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Task
import no.nav.tiltakspenger.libs.ktor.common.oppstart.startApp
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.libs.logging.infra.KotlinLoggingSikkerlogg
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.libs.texas.client.TexasSystemTokenProvider
import no.nav.tiltakspenger.libs.tid.zoneIdOslo
import no.nav.tiltakspenger.soknad.api.antivirus.AvService
import no.nav.tiltakspenger.soknad.api.antivirus.ClamAvClient
import no.nav.tiltakspenger.soknad.api.db.DataSourceSetup
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivClient
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivService
import no.nav.tiltakspenger.soknad.api.identhendelse.IdenthendelseConsumer
import no.nav.tiltakspenger.soknad.api.identhendelse.IdenthendelseService
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import no.nav.tiltakspenger.soknad.api.pdf.PdfClient
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
import kotlin.time.Duration.Companion.seconds

fun main() {
    System.setProperty("logback.configurationFile", Configuration.logbackConfigurationFile())

    val log = KotlinLogging.logger {}
    log.info { "starting server" }
    start(log, clock = Clock.system(zoneIdOslo))
}

internal fun start(
    log: KLogger,
    port: Int = Configuration.httpPort(),
    host: String = "0.0.0.0",
    clock: Clock,
    isNais: Boolean = Configuration.isNais(),
) {
    DefaultExports.initialize()
    val metricsCollector = MetricsCollector(CollectorRegistry.defaultRegistry)

    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        log.error(e) { e.message }
    }
    log.info { "starting server" }

    val dataSource = DataSourceSetup.createDatasource(Configuration.database().url)

    val sikkerlogg: Sikkerlogg = KotlinLoggingSikkerlogg(
        appNavn = Configuration.naisAppName,
        gcpProsjektId = Configuration.gcpTeamProjectId,
    )

    val texasClient = TexasHttpClient(
        introspectionUrl = Configuration.naisTokenIntrospectionEndpoint,
        tokenUrl = Configuration.naisTokenEndpoint,
        tokenExchangeUrl = Configuration.tokenExchangeEndpoint,
        clock = clock,
    )

    // Scope-verdiene våre er på formen `prod-fss:pdl:pdl-api`, så de må skrives om til `api://prod-fss.pdl.pdl-api/.default` før Azure AD godtar dem.
    fun systemTokenProvider(scope: String) = TexasSystemTokenProvider(
        texasClient = texasClient,
        audienceTarget = scope,
    )

    val dokarkivClient = DokarkivClient(
        baseUrl = Configuration.dokarkivUrl,
        clock = clock,
        authTokenProvider = systemTokenProvider(Configuration.dokarkivScope),
    )

    val journalforingService = JournalforingService(
        pdfService = PdfServiceImpl(
            PdfClient(
                pdfEndpoint = Configuration.pdfUrl,
                pdfgenrsEndpoint = Configuration.pdfgenrsUrl,
                isLocalOrDev = Configuration.isLocalOrDev(),
                clock = clock,
            ),
        ),
        dokarkivService = DokarkivService(dokarkivClient),
        sikkerlogg = sikkerlogg,
    )

    val søknadRepo = SøknadRepo(dataSource)
    val pdlService = PdlService(
        clock = clock,
        sikkerlogg = sikkerlogg,
        pdlClient = PdlClient(
            endepunkt = Configuration.pdlUrl,
            clock = clock,
            pdlScope = Configuration.pdlScope,
            texasClient = texasClient,
            authTokenProvider = systemTokenProvider(Configuration.pdlScope),
        ),
    )

    val nySøknadService = NySøknadService(søknadRepo)
    val saksbehandlingApiKlient = SaksbehandlingApiKlient(
        baseUrl = Configuration.saksbehandlingApiUrl,
        clock = clock,
        authTokenProvider = systemTokenProvider(Configuration.saksbehandlingApiScope),
    )

    val søknadJobbService = SøknadJobbService(søknadRepo, pdlService, journalforingService, saksbehandlingApiKlient, clock, sikkerlogg)
    val avService = AvService(
        clamAvClient = ClamAvClient(
            avEndpoint = Configuration.avUrl,
            clock = clock,
        ),
        sikkerlogg = sikkerlogg,
    )
    val tiltakspengerTiltakClient = TiltakspengerTiltakClient(
        tiltakspengerTiltakScope = Configuration.tiltakspengerTiltakScope,
        tiltakspengerTiltakEndpoint = Configuration.tiltakspengerTiltakUrl,
        clock = clock,
        texasClient = texasClient,
    )
    val tiltakService = TiltakService(tiltakspengerTiltakClient, clock, sikkerlogg)

    val identhendelseService = IdenthendelseService(
        søknadRepo = søknadRepo,
    )
    startApp(
        log = log,
        port = port,
        host = host,
        isNais = isNais,
        oppsett = Bakgrunnsprosessoppsett(
            mdcCallIdKey = "call-id",
            electorPath = Configuration::electorPath,
            tasks = listOf(
                Task(
                    navn = "soknad-jobb-hent-saksnummer",
                    intervall = Miljøverdi.lik(60.seconds),
                    utfør = { correlationId ->
                        søknadJobbService.hentEllerOpprettSaksnummer(correlationId)
                        TaskResultat.Ferdig
                    },
                ),
                Task(
                    navn = "soknad-jobb-journalfør",
                    intervall = Miljøverdi.lik(60.seconds),
                    utfør = { correlationId ->
                        søknadJobbService.journalførLagredeSøknader(correlationId)
                        TaskResultat.Ferdig
                    },
                ),
                Task(
                    navn = "soknad-jobb-send-til-saksbehandling",
                    intervall = Miljøverdi.lik(60.seconds),
                    utfør = { correlationId ->
                        søknadJobbService.sendJournalførteSøknaderTilSaksbehandlingApi(correlationId)
                        TaskResultat.Ferdig
                    },
                ),
            ),
            kafkaConsumers = if (isNais) {
                val identhendelseConsumer = IdenthendelseConsumer(
                    identhendelseService = identhendelseService,
                    topic = Configuration.identhendelseTopic,
                )
                listOf(
                    KafkaConsumerOppsett(
                        navn = "identhendelse-consumer",
                        start = { identhendelseConsumer.run() },
                        stopp = { identhendelseConsumer.stop() },
                    ),
                )
            } else {
                emptyList()
            },
            clock = clock,
        ),
    ) { readiness ->
        ktorSetup(
            texasClient = texasClient,
            pdlService = pdlService,
            tiltakService = tiltakService,
            avService = avService,
            metricsCollector = metricsCollector,
            nySøknadService = nySøknadService,
            readiness = readiness,
            clock = clock,
        )
    }
}
