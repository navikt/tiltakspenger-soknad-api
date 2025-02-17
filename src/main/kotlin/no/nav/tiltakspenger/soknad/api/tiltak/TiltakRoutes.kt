package no.nav.tiltakspenger.soknad.api.tiltak

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mu.KotlinLogging
import no.nav.tiltakspenger.libs.logging.sikkerlogg
import no.nav.tiltakspenger.soknad.api.TILTAK_PATH
import no.nav.tiltakspenger.soknad.api.fødselsnummer
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.UGRADERT
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.token

data class TiltakDto(
    val tiltak: List<TiltaksdeltakelseDto>,
)

fun Route.tiltakRoutes(tiltakService: TiltakService, metricsCollector: MetricsCollector, pdlService: PdlService) {
    val log = KotlinLogging.logger { }
    get(TILTAK_PATH) {
        try {
            val fødselsnummer = call.fødselsnummer()
            val subjectToken = call.token()
            if (fødselsnummer == null) {
                throw IllegalStateException("Mangler fødselsnummer")
            }

            val callId = call.callId!!
            val adressebeskyttelse = pdlService.hentAdressebeskyttelse(fødselsnummer, subjectToken, callId)
            val tiltakDto = TiltakDto(
                tiltakService.hentTiltak(
                    subjectToken = subjectToken,
                    maskerArrangørnavn = adressebeskyttelse != UGRADERT,
                ),
            )

            call.respond(tiltakDto)
        } catch (e: Exception) {
            log.error(RuntimeException("Trigger exception for enklere debugging.")) { "Ukjent feil under tiltakroute, se sikkerlogg for mer kontekst." }
            sikkerlogg.error(e) { "Ukjent feil under tiltakroute" }
            metricsCollector.antallFeilVedHentTiltakCounter.inc()
            call.respondText(status = HttpStatusCode.InternalServerError, text = "Internal Server Error")
        }
    }
}
