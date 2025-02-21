package no.nav.tiltakspenger.soknad.api.metrics

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat

fun Route.metricRoutes() {
    get("/metrics") {
        call.respondTextWriter(contentType = ContentType.parse(TextFormat.CONTENT_TYPE_004), status = HttpStatusCode.OK) {
            TextFormat.write004(this, CollectorRegistry.defaultRegistry.metricFamilySamples())
        }
    }
}
