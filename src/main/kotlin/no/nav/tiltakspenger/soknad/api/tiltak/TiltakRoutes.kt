package no.nav.tiltakspenger.soknad.api.tiltak

import arrow.core.getOrElse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.texas.TexasPrincipalExternalUser
import no.nav.tiltakspenger.soknad.api.TILTAK_PATH
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.UGRADERT
import no.nav.tiltakspenger.soknad.api.pdl.PdlService

data class TiltakDto(
    val tiltak: List<TiltaksdeltakelseDto>,
)

private suspend fun ApplicationCall.serverFeil(metricsCollector: MetricsCollector) {
    metricsCollector.antallFeilVedHentTiltakCounter.inc()
    respondText(status = HttpStatusCode.InternalServerError, text = "Internal Server Error")
}

fun Route.tiltakRoutes(
    tiltakService: TiltakService,
    metricsCollector: MetricsCollector,
    pdlService: PdlService,
) {
    val log = KotlinLogging.logger { }
    route(TILTAK_PATH) {
        get {
            log.trace { "Mottok GET-request på /tiltak med callId ${call.callId}" }
            try {
                val principal = call.principal<TexasPrincipalExternalUser>() ?: throw IllegalStateException("Mangler principal")
                val fødselsnummer = principal.fnr
                val subjectToken = principal.token

                val callId = call.callId!!
                // Feilene er allerede logget i servicene; ruta oversetter dem bare til 500, som før migreringen.
                val adressebeskyttelse = pdlService.hentAdressebeskyttelse(fødselsnummer.verdi, subjectToken, callId)
                    .getOrElse { return@get call.serverFeil(metricsCollector) }
                val tiltak = tiltakService.hentTiltak(
                    maskerArrangørnavn = adressebeskyttelse != UGRADERT,
                    fnr = fødselsnummer,
                    correlationId = CorrelationId(callId),
                ).getOrElse { return@get call.serverFeil(metricsCollector) }

                call.respond(TiltakDto(tiltak))
            } catch (e: Exception) {
                log.error(e) { "Ukjent feil under tiltakroute." }
                call.serverFeil(metricsCollector)
            }
        }
    }
}
