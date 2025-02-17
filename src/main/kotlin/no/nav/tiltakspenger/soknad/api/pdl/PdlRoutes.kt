package no.nav.tiltakspenger.soknad.api.pdl

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.tiltakspenger.libs.logging.sikkerlogg
import no.nav.tiltakspenger.soknad.api.PERSONALIA_PATH
import no.nav.tiltakspenger.soknad.api.fødselsnummer
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakService
import no.nav.tiltakspenger.soknad.api.token
import java.time.LocalDate

fun Route.pdlRoutes(pdlService: PdlService, tiltakService: TiltakService, metricsCollector: MetricsCollector) {
    get(PERSONALIA_PATH) {
        try {
            val fødselsnummer = call.fødselsnummer()
            val subjectToken = call.token()

            if (fødselsnummer == null) {
                throw IllegalStateException("Mangler fødselsnummer")
            }

            val tiltak = tiltakService.hentTiltak(subjectToken = subjectToken, maskerArrangørnavn = true)
            val tiltakMedTidligsteFradato = tiltak
                .filter { it.arenaRegistrertPeriode.fra != null }
                .sortedBy { it.arenaRegistrertPeriode.fra }
                .firstOrNull()

            val personDTO = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = fødselsnummer,
                styrendeDato = tiltakMedTidligsteFradato.let { it?.arenaRegistrertPeriode?.fra } ?: LocalDate.now(),
                subjectToken = subjectToken,
                callId = call.callId!!,
            )
            call.respond(personDTO)
        } catch (e: Exception) {
            metricsCollector.antallFeilVedHentPersonaliaCounter.inc()
            sikkerlogg.error(e) { "Feil under pdlRoute" }
            call.respondText(status = HttpStatusCode.InternalServerError, text = "Internal Server Error")
        }
    }
}
