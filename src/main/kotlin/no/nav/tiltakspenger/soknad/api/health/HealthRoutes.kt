package no.nav.tiltakspenger.soknad.api.health

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging

fun Route.healthRoutes(healthChecks: List<HealthCheck>) {
    val log = KotlinLogging.logger { }
    route("/isalive") {
        get {
            val failedHealthChecks = healthChecks.filter { it.status() == HealthStatus.UNHEALTHY }
            if (failedHealthChecks.isNotEmpty()) {
                log.warn { "Failed health checks: $failedHealthChecks" }
                call.respondText(
                    text = "DEAD",
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.ServiceUnavailable,
                )
            } else {
                call.respondText(
                    text = "ALIVE",
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.OK,
                )
            }
        }
    }.also { log.info { "satt opp endepunkt /isalive" } }
    route("/isready") {
        get {
            call.respondText(text = "READY", contentType = ContentType.Text.Plain)
        }
    }.also { log.info { "satt opp endepunkt /isready" } }
}
