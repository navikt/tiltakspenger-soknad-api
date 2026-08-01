package no.nav.tiltakspenger.soknad.api

import io.ktor.http.ContentType
import io.ktor.serialization.jackson3.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.routing.routing
import no.nav.tiltakspenger.libs.json.objectMapper
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Readiness
import no.nav.tiltakspenger.libs.ktor.common.oppstart.healthRoutes
import no.nav.tiltakspenger.libs.texas.IdentityProvider
import no.nav.tiltakspenger.libs.texas.TexasAuthenticationProvider
import no.nav.tiltakspenger.soknad.api.metrics.metricRoutes
import no.nav.tiltakspenger.soknad.api.pdl.routes.pdlRoutes
import no.nav.tiltakspenger.soknad.api.soknad.routes.søknadRoutes
import no.nav.tiltakspenger.soknad.api.soknad.validateSøknad
import no.nav.tiltakspenger.soknad.api.tiltak.tiltakRoutes
import java.util.UUID.randomUUID

fun Application.ktorSetup(
    context: ApplicationContext,
    readiness: Readiness,
) {
    installCallLogging()
    installJacksonFeature()
    install(RequestValidation) {
        validateSøknad(context.clock)
    }

    setupRouting(context, readiness)
}

fun Application.setupRouting(
    context: ApplicationContext,
    readiness: Readiness,
) {
    authentication {
        register(
            TexasAuthenticationProvider(
                TexasAuthenticationProvider.Config(
                    name = IdentityProvider.TOKENX.value,
                    texasClient = context.texasClient,
                    identityProvider = IdentityProvider.TOKENX,
                    requireIdportenLevelHigh = true,
                ),
            ),
        )
    }

    routing {
        authenticate(IdentityProvider.TOKENX.value) {
            pdlRoutes(
                pdlService = context.pdlService,
                tiltakService = context.tiltakService,
                metricsCollector = context.metricsCollector,
            )
            søknadRoutes(
                avService = context.avService,
                metricsCollector = context.metricsCollector,
                nySøknadService = context.nySøknadService,
                clock = context.clock,
            )
            tiltakRoutes(
                tiltakService = context.tiltakService,
                metricsCollector = context.metricsCollector,
                pdlService = context.pdlService,
            )
        }
        healthRoutes { readiness.erKlar() }
        metricRoutes()
    }
}

fun Application.installJacksonFeature() {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(objectMapper))
    }
}

fun Application.installCallLogging() {
    install(CallId) {
        generate { randomUUID().toString() }
    }
    install(CallLogging) {
        callIdMdc(CALL_ID_MDC_KEY)
        filter { call ->
            val path = call.request.path()
            path.startsWith(SØKNAD_PATH) ||
                path.startsWith(PERSONALIA_PATH) ||
                path.startsWith(TILTAK_PATH)
        }
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val path = call.request.path()
            val userAgent = call.request.headers["User-Agent"]
            val callId = call.callId
            "Status: $status, HTTP method: $httpMethod, Path: $path, Call-id: $callId, User agent: $userAgent"
        }
    }
}
