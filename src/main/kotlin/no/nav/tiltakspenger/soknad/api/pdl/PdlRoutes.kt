package no.nav.tiltakspenger.soknad.api.pdl

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.tiltakspenger.soknad.api.PERSONALIA_PATH
import no.nav.tiltakspenger.soknad.api.auth.texas.TexasAuth
import no.nav.tiltakspenger.soknad.api.auth.texas.client.TexasClient
import no.nav.tiltakspenger.soknad.api.auth.texas.fnr
import no.nav.tiltakspenger.soknad.api.auth.texas.token
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakService
import java.time.LocalDate

fun Route.pdlRoutes(
    texasClient: TexasClient,
    pdlService: PdlService,
    tiltakService: TiltakService,
    metricsCollector: MetricsCollector,
) {
    val log = KotlinLogging.logger {}
    route(PERSONALIA_PATH) {
        install(TexasAuth) { client = texasClient }
        get {
            try {
                val fødselsnummer = call.fnr()
                val subjectToken = call.token()

                val tiltak = tiltakService.hentTiltak(subjectToken = subjectToken, maskerArrangørnavn = true)
                val tiltakMedTidligsteFradato = tiltak
                    .filter { it.arenaRegistrertPeriode.fra != null }
                    .sortedBy { it.arenaRegistrertPeriode.fra }
                    .firstOrNull()

                val personDTO = pdlService.hentPersonaliaMedBarn(
                    fødselsnummer = fødselsnummer.verdi,
                    styrendeDato = tiltakMedTidligsteFradato.let { it?.arenaRegistrertPeriode?.fra } ?: LocalDate.now(),
                    subjectToken = subjectToken,
                    callId = call.callId!!,
                )
                call.respond(personDTO)
            } catch (e: Exception) {
                metricsCollector.antallFeilVedHentPersonaliaCounter.inc()
                log.error(e) { "Feil under pdlRoute" }
                call.respondText(status = HttpStatusCode.InternalServerError, text = "Internal Server Error")
            }
        }
    }
}
