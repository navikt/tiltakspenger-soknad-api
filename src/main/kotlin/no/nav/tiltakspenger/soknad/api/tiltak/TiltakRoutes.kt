package no.nav.tiltakspenger.soknad.api.tiltak

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.tiltakspenger.soknad.api.TILTAK_PATH
import no.nav.tiltakspenger.soknad.api.auth.texas.TexasPrincipal
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.UGRADERT
import no.nav.tiltakspenger.soknad.api.pdl.PdlService

data class TiltakDto(
    val tiltak: List<TiltaksdeltakelseDto>,
)

fun Route.tiltakRoutes(
    tiltakService: TiltakService,
    metricsCollector: MetricsCollector,
    pdlService: PdlService,
) {
    val log = KotlinLogging.logger { }
    route(TILTAK_PATH) {
        get {
            try {
                val principal = call.principal<TexasPrincipal>() ?: throw IllegalStateException("Mangler principal")
                val fødselsnummer = principal.fnr
                val subjectToken = principal.token

                val callId = call.callId!!
                val adressebeskyttelse = pdlService.hentAdressebeskyttelse(fødselsnummer.verdi, subjectToken, callId)
                val tiltakDto = TiltakDto(
                    tiltakService.hentTiltak(
                        subjectToken = subjectToken,
                        maskerArrangørnavn = adressebeskyttelse != UGRADERT,
                    ),
                )

                call.respond(tiltakDto)
            } catch (e: Exception) {
                log.error(e) { "Ukjent feil under tiltakroute." }
                metricsCollector.antallFeilVedHentTiltakCounter.inc()
                call.respondText(status = HttpStatusCode.InternalServerError, text = "Internal Server Error")
            }
        }
    }
}
