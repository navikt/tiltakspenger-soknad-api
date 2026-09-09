package no.nav.tiltakspenger.soknad.api.metrics

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

/**
 * Eksponerer registeret appen selv eier.
 * Registeret kommer inn som parameter i stedet for å slås opp globalt, slik at rutene, jobbene og meldingsleseren fører målingene sine i nøyaktig det registeret som skrapes.
 */
fun Route.metricRoutes(meterRegistry: PrometheusMeterRegistry) {
    get("/metrics") {
        call.respondText(
            text = meterRegistry.scrape(),
            status = HttpStatusCode.OK,
        )
    }
}
