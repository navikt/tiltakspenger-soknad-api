package no.nav.tiltakspenger.soknad.api

import arrow.core.Either
import arrow.core.right
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.AttributeKey
import io.prometheus.client.hotspot.DefaultExports
import no.nav.tiltakspenger.libs.jobber.LeaderPodLookup
import no.nav.tiltakspenger.libs.jobber.LeaderPodLookupClient
import no.nav.tiltakspenger.libs.jobber.LeaderPodLookupFeil
import no.nav.tiltakspenger.libs.jobber.RunCheckFactory
import no.nav.tiltakspenger.soknad.api.Configuration.httpPort
import no.nav.tiltakspenger.soknad.api.antivirus.AvClient
import no.nav.tiltakspenger.soknad.api.antivirus.AvService
import no.nav.tiltakspenger.soknad.api.antivirus.AvServiceImpl
import no.nav.tiltakspenger.soknad.api.auth.texas.client.TexasClient
import no.nav.tiltakspenger.soknad.api.db.flywayMigrate
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivClient
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivService
import no.nav.tiltakspenger.soknad.api.identhendelse.IdenthendelseConsumer
import no.nav.tiltakspenger.soknad.api.identhendelse.IdenthendelseService
import no.nav.tiltakspenger.soknad.api.jobber.TaskExecutor
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import no.nav.tiltakspenger.soknad.api.pdf.PdfClient
import no.nav.tiltakspenger.soknad.api.pdf.PdfServiceImpl
import no.nav.tiltakspenger.soknad.api.pdl.PdlClientTokenX
import no.nav.tiltakspenger.soknad.api.pdl.PdlCredentialsClient
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.saksbehandlingApi.SaksbehandlingApiKlient
import no.nav.tiltakspenger.soknad.api.soknad.NySøknadService
import no.nav.tiltakspenger.soknad.api.soknad.SøknadRepo
import no.nav.tiltakspenger.soknad.api.soknad.jobb.SøknadJobbService
import no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforendeEnhet.JournalforendeEnhetService
import no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforendeEnhet.arbeidsfordeling.ArbeidsfordelingClient
import no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforing.JournalforingService
import no.nav.tiltakspenger.soknad.api.soknad.jobb.person.PersonHttpklient
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakService
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakspengerTiltakClient

fun main() {
    System.setProperty("logback.configurationFile", Configuration.logbackConfigurationFile())

    val log = KotlinLogging.logger {}
    log.info { "starting server" }
    start(log)
}

internal fun start(
    log: KLogger,
    port: Int = httpPort(),
) {
    DefaultExports.initialize()
    val metricsCollector = MetricsCollector()

    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        log.error(e) { e.message }
    }
    log.info { "starting server" }

    flywayMigrate()

    val texasClient = TexasClient(
        introspectionUrl = Configuration.naisTokenIntrospectionEndpoint,
        tokenUrl = Configuration.naisTokenEndpoint,
        tokenExchangeUrl = Configuration.tokenExchangeEndpoint,
    )

    val personHttpklient = PersonHttpklient(Configuration.pdlUrl) {
        texasClient.getSystemToken(audienceTarget = Configuration.pdlScope)
    }
    val arbeidsfordelingClient = ArbeidsfordelingClient(baseUrl = Configuration.norg2Url) {
        texasClient.getSystemToken(audienceTarget = Configuration.norg2Scope)
    }
    val journalforendeEnhetService = JournalforendeEnhetService(arbeidsfordelingClient)

    val dokarkivClient = DokarkivClient(baseUrl = Configuration.dokarkivUrl) {
        texasClient.getSystemToken(audienceTarget = Configuration.dokarkivScope)
    }

    val journalforingService = JournalforingService(
        pdfService = PdfServiceImpl(
            PdfClient(
                pdfEndpoint = Configuration.pdfUrl,
                client = httpClientCIO(timeout = 30L),
            ),
        ),
        dokarkivService = DokarkivService(dokarkivClient),
    )

    val søknadRepo = SøknadRepo()
    val pdlService = PdlService(
        pdlClientTokenX = PdlClientTokenX(
            pdlEndpoint = Configuration.pdlUrl,
            pdlScope = Configuration.pdlScope,
            texasClient = texasClient,
        ),
        pdlClientCredentials = PdlCredentialsClient(pdlEndpoint = Configuration.pdlUrl) {
            texasClient.getSystemToken(audienceTarget = Configuration.pdlScope)
        },
    )

    val nySøknadService = NySøknadService(søknadRepo)
    val saksbehandlingApiKlient = SaksbehandlingApiKlient(baseUrl = Configuration.saksbehandlingApiUrl) {
        texasClient.getSystemToken(audienceTarget = Configuration.saksbehandlingApiScope)
    }

    val søknadJobbService = SøknadJobbService(søknadRepo, personHttpklient, journalforingService, saksbehandlingApiKlient)
    val avService: AvService = AvServiceImpl(
        av = AvClient(
            avEndpoint = Configuration.avUrl,
            client = httpClientCIO(timeout = 30L),
        ),
    )
    val tiltakspengerTiltakClient = TiltakspengerTiltakClient(
        tiltakspengerTiltakScope = Configuration.tiltakspengerTiltakScope,
        tiltakspengerTiltakEndpoint = Configuration.tiltakspengerTiltakUrl,
        texasClient = texasClient,
    )
    val tiltakService = TiltakService(tiltakspengerTiltakClient)

    val identhendelseService = IdenthendelseService(
        søknadRepo = søknadRepo,
    )
    val identhendelseConsumer = IdenthendelseConsumer(
        identhendelseService = identhendelseService,
        topic = Configuration.identhendelseTopic,
    )

    val server = embeddedServer(
        factory = Netty,
        port = port,
        module = {
            ktorSetup(
                texasClient = texasClient,
                pdlService = pdlService,
                tiltakService = tiltakService,
                avService = avService,
                metricsCollector = metricsCollector,
                nySøknadService = nySøknadService,
            )
        },
    )

    server.application.attributes.put(isReadyKey, true)

    val runCheckFactory =
        if (Configuration.isNais()) {
            RunCheckFactory(
                leaderPodLookup =
                LeaderPodLookupClient(
                    electorPath = Configuration.electorPath(),
                    logger = KotlinLogging.logger { },
                ),
                applicationIsReady = { server.application.isReady() },
            )
        } else {
            RunCheckFactory(
                leaderPodLookup =
                object : LeaderPodLookup {
                    override fun amITheLeader(localHostName: String): Either<LeaderPodLookupFeil, Boolean> =
                        true.right()
                },
                applicationIsReady = { server.application.isReady() },
            )
        }
    TaskExecutor.startJob(
        runCheckFactory = runCheckFactory,
        tasks =
        listOf { correlationId ->
            søknadJobbService.hentEllerOpprettSaksnummer(correlationId)
            søknadJobbService.journalførLagredeSøknader(correlationId)
            søknadJobbService.sendJournalførteSøknaderTilSaksbehandlingApi(correlationId)
        },
    )

    if (Configuration.isNais()) {
        val consumers = listOf(
            identhendelseConsumer,
        )
        consumers.forEach { it.run() }
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.application.attributes.put(isReadyKey, false)
            server.stop(gracePeriodMillis = 5_000, timeoutMillis = 30_000)
        },
    )
    log.info { "Starter server" }
    server.start(wait = true)
}
val isReadyKey = AttributeKey<Boolean>("isReady")

fun Application.isReady() = attributes.getOrNull(isReadyKey) == true
