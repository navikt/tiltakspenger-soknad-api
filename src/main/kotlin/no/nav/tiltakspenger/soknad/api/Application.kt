package no.nav.tiltakspenger.soknad.api

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.prometheus.client.hotspot.DefaultExports
import no.nav.tiltakspenger.libs.jobber.TaskResultat
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Bakgrunnsprosessoppsett
import no.nav.tiltakspenger.libs.ktor.common.oppstart.KafkaConsumerOppsett
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Miljøverdi
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Task
import no.nav.tiltakspenger.libs.ktor.common.oppstart.startApp
import no.nav.tiltakspenger.libs.texas.IdentityProvider
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.libs.tid.zoneIdOslo
import no.nav.tiltakspenger.soknad.api.antivirus.AvService
import no.nav.tiltakspenger.soknad.api.antivirus.ClamAvClient
import no.nav.tiltakspenger.soknad.api.db.DataSource
import no.nav.tiltakspenger.soknad.api.db.flywayMigrate
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
    clock: Clock,
    isNais: Boolean = Configuration.isNais(),
) {
    DefaultExports.initialize()
    val metricsCollector = MetricsCollector()

    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        log.error(e) { e.message }
    }
    log.info { "starting server" }

    flywayMigrate()

    val texasClient = TexasHttpClient(
        introspectionUrl = Configuration.naisTokenIntrospectionEndpoint,
        tokenUrl = Configuration.naisTokenEndpoint,
        tokenExchangeUrl = Configuration.tokenExchangeEndpoint,
        clock = clock,
    )

    val dokarkivClient = DokarkivClient(baseUrl = Configuration.dokarkivUrl) {
        texasClient.getSystemToken(
            audienceTarget = Configuration.dokarkivScope,
            identityProvider = IdentityProvider.AZUREAD,
        )
    }

    val journalforingService = JournalforingService(
        pdfService = PdfServiceImpl(
            PdfClient(
                pdfEndpoint = Configuration.pdfUrl,
                pdfgenrsEndpoint = Configuration.pdfgenrsUrl,
                isLocalOrDev = Configuration.isLocalOrDev(),
                client = httpClientCIO(timeout = 30L),
            ),
        ),
        dokarkivService = DokarkivService(dokarkivClient),
    )

    val søknadRepo = SøknadRepo(DataSource.dataSource)
    val pdlService = PdlService(
        clock = clock,
        pdlClient = PdlClient(
            endepunkt = Configuration.pdlUrl,
            clock = clock,
            pdlScope = Configuration.pdlScope,
            texasClient = texasClient,
        ) {
            texasClient.getSystemToken(
                audienceTarget = Configuration.pdlScope,
                identityProvider = IdentityProvider.AZUREAD,
            )
        },
    )

    val nySøknadService = NySøknadService(søknadRepo)
    val saksbehandlingApiKlient = SaksbehandlingApiKlient(baseUrl = Configuration.saksbehandlingApiUrl) {
        texasClient.getSystemToken(
            audienceTarget = Configuration.saksbehandlingApiScope,
            identityProvider = IdentityProvider.AZUREAD,
        )
    }

    val søknadJobbService = SøknadJobbService(søknadRepo, pdlService, journalforingService, saksbehandlingApiKlient, clock)
    val avService = AvService(
        clamAvClient = ClamAvClient(
            avEndpoint = Configuration.avUrl,
            client = httpClientCIO(timeout = 30L),
        ),
    )
    val tiltakspengerTiltakClient = TiltakspengerTiltakClient(
        tiltakspengerTiltakScope = Configuration.tiltakspengerTiltakScope,
        tiltakspengerTiltakEndpoint = Configuration.tiltakspengerTiltakUrl,
        texasClient = texasClient,
    )
    val tiltakService = TiltakService(tiltakspengerTiltakClient, clock)

    val identhendelseService = IdenthendelseService(
        søknadRepo = søknadRepo,
    )
    val identhendelseConsumer = IdenthendelseConsumer(
        identhendelseService = identhendelseService,
        topic = Configuration.identhendelseTopic,
    )

    startApp(
        log = log,
        port = port,
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
                listOf(
                    KafkaConsumerOppsett(
                        navn = "identhendelse-consumer",
                        start = { identhendelseConsumer.run() },
                        stopp = {},
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
