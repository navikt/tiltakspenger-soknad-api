package no.nav.tiltakspenger.soknad.api

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.routing.routing
import no.nav.tiltakspenger.soknad.api.antivirus.AvService
import no.nav.tiltakspenger.soknad.api.auth.texas.client.TexasClient
import no.nav.tiltakspenger.soknad.api.health.healthRoutes
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import no.nav.tiltakspenger.soknad.api.metrics.metricRoutes
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.pdl.pdlRoutes
import no.nav.tiltakspenger.soknad.api.soknad.NySøknadService
import no.nav.tiltakspenger.soknad.api.soknad.routes.søknadRoutes
import no.nav.tiltakspenger.soknad.api.soknad.validateSøknad
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakService
import no.nav.tiltakspenger.soknad.api.tiltak.tiltakRoutes
import java.util.UUID.randomUUID

internal fun Application.ktorSetup(
    texasClient: TexasClient,
    pdlService: PdlService,
    tiltakService: TiltakService,
    avService: AvService,
    metricsCollector: MetricsCollector,
    nySøknadService: NySøknadService,
) {
    installCallLogging()
    installJacksonFeature()
    install(RequestValidation) {
        validateSøknad()
    }

    setupRouting(
        texasClient = texasClient,
        pdlService = pdlService,
        tiltakService = tiltakService,
        avService = avService,
        metricsCollector = metricsCollector,
        nySøknadService = nySøknadService,
    )
}

internal fun Application.setupRouting(
    texasClient: TexasClient,
    pdlService: PdlService,
    nySøknadService: NySøknadService,
    tiltakService: TiltakService,
    avService: AvService,
    metricsCollector: MetricsCollector,
) {
    routing {
        pdlRoutes(
            texasClient = texasClient,
            pdlService = pdlService,
            tiltakService = tiltakService,
            metricsCollector = metricsCollector,
        )
        søknadRoutes(
            texasClient = texasClient,
            avService = avService,
            metricsCollector = metricsCollector,
            nySøknadService = nySøknadService,
        )
        tiltakRoutes(
            texasClient = texasClient,
            tiltakService = tiltakService,
            metricsCollector = metricsCollector,
            pdlService = pdlService,
        )
        healthRoutes()
        metricRoutes()
    }
}

internal fun Application.installJacksonFeature() {
    install(ContentNegotiation) {
        jackson {
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            registerModule(JavaTimeModule())
            registerModule(KotlinModule.Builder().build())
        }
    }
}

internal fun Application.installCallLogging() {
    install(CallId) {
        generate { randomUUID().toString() }
    }
    install(CallLogging) {
        callIdMdc("call-id")
        filter { call ->
            call.request.path().startsWith("/$SØKNAD_PATH")
            call.request.path().startsWith("/$PERSONALIA_PATH")
        }
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val req = call.request
            val userAgent = call.request.headers["User-Agent"]
            "Status: $status, HTTP method: $httpMethod, User agent: $userAgent req: $req"
        }
    }
}
