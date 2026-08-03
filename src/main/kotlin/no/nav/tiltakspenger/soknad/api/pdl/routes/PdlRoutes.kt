package no.nav.tiltakspenger.soknad.api.pdl.routes

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
import no.nav.tiltakspenger.soknad.api.PERSONALIA_PATH
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakService

private suspend fun ApplicationCall.serverFeil(metricsCollector: MetricsCollector) {
    metricsCollector.antallFeilVedHentPersonaliaCounter.inc()
    respondText(status = HttpStatusCode.InternalServerError, text = "Internal Server Error")
}

fun Route.pdlRoutes(
    pdlService: PdlService,
    tiltakService: TiltakService,
    metricsCollector: MetricsCollector,
) {
    val log = KotlinLogging.logger {}
    route(PERSONALIA_PATH) {
        get {
            try {
                val principal = call.principal<TexasPrincipalExternalUser>() ?: throw IllegalStateException("Mangler principal")
                val fødselsnummer = principal.fnr
                val subjectToken = principal.token

                // Feilene er allerede logget i servicene; ruta oversetter dem bare til 500, som før migreringen.
                val tiltak = tiltakService.hentTiltak(
                    subjectToken = subjectToken,
                    fnr = fødselsnummer,
                    maskerArrangørnavn = true,
                    correlationId = CorrelationId(call.callId!!),
                ).getOrElse { return@get call.serverFeil(metricsCollector) }
                val tiltakMedTidligsteFradato = tiltak
                    .filter { it.arenaRegistrertPeriode.fra != null }
                    .sortedBy { it.arenaRegistrertPeriode.fra }
                    .firstOrNull()

                val personDTO = pdlService.hentPersonaliaMedBarn(
                    fødselsnummer = fødselsnummer.verdi,
                    styrendeDato = tiltakMedTidligsteFradato?.arenaRegistrertPeriode?.fra,
                    subjectToken = subjectToken,
                    callId = call.callId!!,
                ).getOrElse { return@get call.serverFeil(metricsCollector) }
                call.respond(personDTO)
            } catch (e: Exception) {
                log.error(e) { "Feil under pdlRoute" }
                call.serverFeil(metricsCollector)
            }
        }
    }
}
