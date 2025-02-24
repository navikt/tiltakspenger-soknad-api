package no.nav.tiltakspenger.soknad.api.tiltak

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.tiltakspenger.libs.logging.sikkerlogg
import no.nav.tiltakspenger.soknad.api.TILTAK_PATH
import no.nav.tiltakspenger.soknad.api.auth.texas.TexasAuth
import no.nav.tiltakspenger.soknad.api.auth.texas.client.TexasClient
import no.nav.tiltakspenger.soknad.api.auth.texas.fnr
import no.nav.tiltakspenger.soknad.api.auth.texas.token
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.UGRADERT
import no.nav.tiltakspenger.soknad.api.pdl.PdlService

data class TiltakDto(
    val tiltak: List<TiltaksdeltakelseDto>,
)

fun Route.tiltakRoutes(
    texasClient: TexasClient,
    tiltakService: TiltakService,
    metricsCollector: MetricsCollector,
    pdlService: PdlService,
) {
    val log = KotlinLogging.logger { }
    route(TILTAK_PATH) {
        install(TexasAuth) { client = texasClient }
        get {
            try {
                val fødselsnummer = call.fnr()
                val subjectToken = call.token()

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
                log.error(RuntimeException("Trigger exception for enklere debugging.")) { "Ukjent feil under tiltakroute, se sikkerlogg for mer kontekst." }
                sikkerlogg.error(e) { "Ukjent feil under tiltakroute" }
                metricsCollector.antallFeilVedHentTiltakCounter.inc()
                call.respondText(status = HttpStatusCode.InternalServerError, text = "Internal Server Error")
            }
        }
    }
}
