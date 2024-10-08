package no.nav.tiltakspenger.soknad.api.soknad

import arrow.core.Either
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import mu.KotlinLogging
import no.nav.tiltakspenger.soknad.api.SØKNAD_PATH
import no.nav.tiltakspenger.soknad.api.acr
import no.nav.tiltakspenger.soknad.api.antivirus.AvService
import no.nav.tiltakspenger.soknad.api.antivirus.MalwareFoundException
import no.nav.tiltakspenger.soknad.api.fødselsnummer
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import java.time.LocalDateTime

val LOG = KotlinLogging.logger { }
private val securelog = KotlinLogging.logger("tjenestekall")

fun Route.søknadRoutes(
    søknadService: SøknadService,
    søknadRepo: SøknadRepo,
    avService: AvService,
    pdlService: PdlService,
    metricsCollector: MetricsCollector,
) {
    post(SØKNAD_PATH) {
        val requestTimer = metricsCollector.SØKNADSMOTTAK_LATENCY_SECONDS.startTimer()
        try {
            val innsendingTidspunkt = LocalDateTime.now()
            val (søknad, vedlegg) = søknadService.taInnSøknadSomMultipart(call.receiveMultipart())
            avService.gjørVirussjekkAvVedlegg(vedlegg)
            val fødselsnummer = call.fødselsnummer() ?: throw IllegalStateException("Mangler fødselsnummer")
            val acr = call.acr() ?: "Ingen Level"

            Either.catch {
                søknadRepo.lagre(
                    mapSøknad(
                        spm = søknad,
                        acr = acr,
                        fnr = fødselsnummer,
                        vedlegg = vedlegg,
                        innsendingTidspunkt = innsendingTidspunkt,
                    ),
                )
            }.onLeft {
                securelog.error("Feil ved lagring av søknad", it)
            }

            // Dette kan flyttes ut til funksjoner med try/catch og logging
            // Kan legge til egen teller som teller antall søknader som er journalført og sendt til vedtak
            metricsCollector.ANTALL_SØKNADER_MOTTATT_COUNTER.inc()
            requestTimer.observeDuration()

            val søknadResponse = SøknadResponse(
                journalpostId = "ikkeJournalførtEnda",
                innsendingTidspunkt = innsendingTidspunkt,
            )

            call.respond(status = HttpStatusCode.Created, message = søknadResponse)
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
                    securelog.error("Ugyldig søknad ${exception.message}", exception)
                    metricsCollector.ANTALL_FEILEDE_INNSENDINGER_COUNTER.inc()
                    metricsCollector.ANTALL_UGYLDIGE_SØKNADER_COUNTER.inc()
                    requestTimer.observeDuration()
                    call.respondText(
                        text = "Bad Request",
                        contentType = ContentType.Text.Plain,
                        status = HttpStatusCode.BadRequest,
                    )
                }

                else -> {
                    LOG.error("Noe gikk galt ved post av søknad ${exception.message}", exception)
                    metricsCollector.ANTALL_FEILEDE_INNSENDINGER_COUNTER.inc()
                    requestTimer.observeDuration()
                    call.respondText(
                        text = "Internal server error",
                        contentType = ContentType.Text.Plain,
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            }
        }
    }.also { LOG.info { "satt opp endepunkt /soknad" } }
}
