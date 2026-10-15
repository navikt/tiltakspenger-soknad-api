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
import no.nav.tiltakspenger.soknad.api.pdl.routes.dto.BarnDTO
import no.nav.tiltakspenger.soknad.api.pdl.routes.dto.BarnListeDTO
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakService

private suspend fun ApplicationCall.serverFeil(metricsCollector: MetricsCollector) {
    metricsCollector.antallFeilVedHentPersonaliaCounter.increment()
    respondText(status = HttpStatusCode.InternalServerError, text = "Internal Server Error")
}

/**
 * `GET /personalia` svarer med søkerens personalia, uten barn.
 * Søknaden henter barna fra `GET /personalia/barn` når søkeren har svart at hen vil søke barnetillegg.
 */
fun Route.pdlRoutes(
    pdlService: PdlService,
    tiltakService: TiltakService,
    metricsCollector: MetricsCollector,
) {
    val log = KotlinLogging.logger {}

    /** Feilene er allerede logget i servicene; null betyr at ruta skal svare 500. */
    suspend fun ApplicationCall.hentBarn(principal: TexasPrincipalExternalUser): List<BarnDTO>? {
        val tiltak = tiltakService.hentTiltak(
            fnr = principal.fnr,
            maskerArrangørnavn = true,
            correlationId = CorrelationId(callId!!),
        ).getOrElse { return null }
        val tiltakMedTidligsteFradato = tiltak
            .filter { it.arenaRegistrertPeriode.fra != null }
            .sortedBy { it.arenaRegistrertPeriode.fra }
            .firstOrNull()

        return pdlService.hentBarn(
            fødselsnummer = principal.fnr.verdi,
            styrendeDato = tiltakMedTidligsteFradato?.arenaRegistrertPeriode?.fra,
            subjectToken = principal.token,
            callId = callId!!,
        ).getOrElse { null }
    }

    route(PERSONALIA_PATH) {
        get {
            try {
                val principal = call.principal<TexasPrincipalExternalUser>() ?: throw IllegalStateException("Mangler principal")
                val personDTO = pdlService.hentPersonalia(
                    fødselsnummer = principal.fnr.verdi,
                    subjectToken = principal.token,
                    callId = call.callId!!,
                ).getOrElse { return@get call.serverFeil(metricsCollector) }
                call.respond(personDTO)
            } catch (e: Exception) {
                log.error(e) { "Feil under pdlRoute" }
                call.serverFeil(metricsCollector)
            }
        }
        get("/barn") {
            try {
                val principal = call.principal<TexasPrincipalExternalUser>() ?: throw IllegalStateException("Mangler principal")
                val barn = call.hentBarn(principal) ?: return@get call.serverFeil(metricsCollector)
                call.respond(BarnListeDTO(barn = barn))
            } catch (e: Exception) {
                log.error(e) { "Feil under pdlRoute for barn" }
                call.serverFeil(metricsCollector)
            }
        }
    }
}
