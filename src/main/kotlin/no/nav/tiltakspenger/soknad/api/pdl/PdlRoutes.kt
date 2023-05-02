package no.nav.tiltakspenger.soknad.api.pdl

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mu.KotlinLogging
import no.nav.tiltakspenger.soknad.api.PERSONALIA_PATH
import no.nav.tiltakspenger.soknad.api.fødselsnummer
import no.nav.tiltakspenger.soknad.api.token

val secureLog = KotlinLogging.logger("tjenestekall")

fun Route.pdlRoutes(pdlService: PdlService) {
    get(path = PERSONALIA_PATH) {
        try {
            val fødselsnummer = call.fødselsnummer()
            val subjectToken = call.token()

            if (fødselsnummer == null) {
                throw IllegalStateException("Mangler fødselsnummer")
            }
            val personDTO = pdlService.hentPersonaliaMedBarn(fødselsnummer = fødselsnummer, subjectToken = subjectToken)
            call.respond(personDTO)
        } catch (e: Exception) {
            secureLog.error("Feil under kall mot PDL", e)
            call.respondText(status = HttpStatusCode.InternalServerError, text = "Internal Server Error")
        }
    }
}
