package no.nav.tiltakspenger.soknad.api.soknad.routes

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.tiltakspenger.libs.texas.TexasPrincipalExternalUser
import no.nav.tiltakspenger.soknad.api.SØKNAD_PATH
import no.nav.tiltakspenger.soknad.api.antivirus.AvService
import no.nav.tiltakspenger.soknad.api.antivirus.MalwareFoundException
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.soknad.NySøknadCommand
import no.nav.tiltakspenger.soknad.api.soknad.NySøknadService
import java.time.LocalDateTime

fun Route.søknadRoutes(
    nySøknadService: NySøknadService,
    avService: AvService,
    metricsCollector: MetricsCollector,
    pdlService: PdlService,
) {
    val log = KotlinLogging.logger { }
    route(SØKNAD_PATH) {
        post {
            log.info { "Mottatt kall til $SØKNAD_PATH" }
            val requestTimer = metricsCollector.søknadsmottakLatencySeconds.startTimer()
            try {
                val principal =
                    call.principal<TexasPrincipalExternalUser>() ?: throw IllegalStateException("Mangler principal")
                val innsendingTidspunkt = LocalDateTime.now()
                val (brukersBesvarelser, vedlegg) = taInnSøknadSomMultipart(call.receiveMultipart())
                log.info { "Utfører virussjekk" }
                avService.gjørVirussjekkAvVedlegg(vedlegg)
                val fødselsnummer = principal.fnr
                val acr = principal.claims["acr"]!!.toString()
                val adressebeskyttelse = pdlService.hentAdressebeskyttelse(
                    fødselsnummer = fødselsnummer.verdi,
                    subjectToken = principal.token,
                    callId = call.callId!!,
                )

                val command = NySøknadCommand(
                    brukersBesvarelser = brukersBesvarelser,
                    acr = acr,
                    fnr = fødselsnummer.verdi,
                    vedlegg = vedlegg,
                    innsendingTidspunkt = innsendingTidspunkt,
                )
                nySøknadService.nySøknad(command, adressebeskyttelse).fold(
                    {
                        metricsCollector.antallFeiledeInnsendingerCounter.inc()
                        requestTimer.observeDuration()
                        call.respondText(
                            text = "Internal server error",
                            contentType = ContentType.Text.Plain,
                            status = HttpStatusCode.InternalServerError,
                        )
                    },
                    {
                        // Dette kan flyttes ut til funksjoner med try/catch og logging
                        // Kan legge til egen teller som teller antall søknader som er journalført og sendt til saksbehandling-apo
                        metricsCollector.antallSøknaderMottattCounter.inc()
                        requestTimer.observeDuration()

                        val søknadResponse = SøknadResponse(
                            innsendingTidspunkt = innsendingTidspunkt,
                        )
                        call.respond(status = HttpStatusCode.Created, message = søknadResponse)
                    },
                )
            } catch (exception: Exception) {
                when (exception) {
                    is CannotTransformContentToTypeException,
                    is BadRequestException,
                    is MissingContentException,
                    is UnrecognizedFormItemException,
                    is MalwareFoundException,
                    is UninitializedPropertyAccessException,
                    is RequestValidationException,
                    -> {
                        log.error(exception) { "Ugyldig søknad: ${exception.message}" }
                        metricsCollector.antallFeiledeInnsendingerCounter.inc()
                        metricsCollector.antallUgyldigeSøknaderCounter.inc()
                        requestTimer.observeDuration()
                        call.respondText(
                            text = "Bad Request",
                            contentType = ContentType.Text.Plain,
                            status = HttpStatusCode.BadRequest,
                        )
                    }

                    else -> {
                        log.error(exception) { "Noe gikk galt ved post av søknad ${exception.message}" }
                        metricsCollector.antallFeiledeInnsendingerCounter.inc()
                        requestTimer.observeDuration()
                        call.respondText(
                            text = "Internal server error",
                            contentType = ContentType.Text.Plain,
                            status = HttpStatusCode.InternalServerError,
                        )
                    }
                }
            }
        }
    }.also { log.info { "satt opp endepunkt /soknad" } }
}
