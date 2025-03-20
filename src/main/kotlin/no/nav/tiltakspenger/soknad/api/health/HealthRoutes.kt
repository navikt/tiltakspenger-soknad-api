package no.nav.tiltakspenger.soknad.api.health

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.tiltakspenger.soknad.api.isReady

fun Route.healthRoutes() {
    val log = KotlinLogging.logger { }

    get("/isalive") {
        call.respondText("ALIVE")
    }.also { log.info { "satt opp endepunkt /isalive" } }

    get("/isready") {
        if (call.application.isReady()) {
            call.respondText("READY")
        } else {
            call.respondText("NOT READY", status = HttpStatusCode.ServiceUnavailable)
        }
    }.also { log.info { "satt opp endepunkt /isready" } }
}
