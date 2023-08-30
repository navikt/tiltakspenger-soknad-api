package no.nav.tiltakspenger.soknad.api.joark

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.ApplicationConfig
import no.nav.tiltakspenger.soknad.api.httpClientWithRetry
import no.nav.tiltakspenger.soknad.api.objectMapper
import no.nav.tiltakspenger.soknad.api.pdl.INDIVIDSTONAD
import org.slf4j.LoggerFactory

internal const val joarkPath = "rest/journalpostapi/v1/journalpost"

class JoarkClient(
    private val config: ApplicationConfig,
    private val client: HttpClient = httpClientWithRetry(timeout = 30L),
    private val joarkCredentialsClient: JoarkCredentialsClient = JoarkCredentialsClient(config),
) {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val joarkEndpoint = config.property("endpoints.joark").getString()

    suspend fun opprettJournalpost(
        dokumentInnhold: Journalpost,
        callId: String,
    ): String {
        try {
            log.info("Starter journalføring av søknad")
            val token = joarkCredentialsClient.getToken()
            val res = client.post("$joarkEndpoint/$joarkPath") {
                accept(ContentType.Application.Json)
                header("X-Correlation-ID", INDIVIDSTONAD)
                header("Nav-Callid", callId)
                parameter("forsoekFerdigstill", false)
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(
                    objectMapper.writeValueAsString(
                        JournalpostRequest(
                            tittel = dokumentInnhold.tittel,
                            journalpostType = dokumentInnhold.journalpostType,
                            tema = dokumentInnhold.tema,
                            kanal = dokumentInnhold.kanal,
                            behandlingstema = dokumentInnhold.behandlingstema,
                            // journalfoerendeEnhet = dokumentInnhold.journalfoerendeEnhet,
                            avsenderMottaker = dokumentInnhold.avsenderMottaker,
                            bruker = dokumentInnhold.bruker,
                            // sak = dokumentInnhold.sak,
                            dokumenter = dokumentInnhold.dokumenter,
                            eksternReferanseId = callId,
                        ),
                    ),
                )
            }

            when (res.status) {
                HttpStatusCode.Created -> {
                    val response = res.call.body<JoarkResponse>()

                    val journalpostId = if (response.journalpostId.isNullOrEmpty()) {
                        log.error("Kallet til Joark gikk ok, men vi fikk ingen journalpostId fra Joark")
                        throw IllegalStateException("Kallet til Joark gikk ok, men vi fikk ingen journalpostId fra Joark")
                    } else {
                        response.journalpostId
                    }

                    // if ((response.journalpostferdigstilt == null) || (response.journalpostferdigstilt == false)) {
                    //     log.error("Kunne ikke ferdigstille journalføring for journalpostId: $journalpostId. response=$response")
                    //     throw IllegalStateException("Kunne ikke ferdigstille journalføring for journalpostId: $journalpostId. response=$response")
                    // }

                    log.info("Vi har opprettet journalpost med id : $journalpostId")
                    return journalpostId
                }

                else -> {
                    log.error("Kallet til joark feilet ${res.status} ${res.status.description}")
                    throw RuntimeException("Feil i kallet til joark")
                }
            }
        } catch (throwable: Throwable) {
            if (throwable is ClientRequestException && throwable.response.status == HttpStatusCode.Conflict) {
                log.warn("Søknaden har allerede blitt journalført (409 Conflict)")
                val response = throwable.response.call.body<JoarkResponse>()
                return response.journalpostId.orEmpty()
            }
            if (throwable is IllegalStateException) {
                throw throwable
            } else {
                log.error("Kallet til joark feilet $throwable")
                throw RuntimeException("Feil i kallet til joark $throwable")
            }
        }
    }

    data class JoarkResponse(
        val journalpostId: String?,
        val journalpostferdigstilt: Boolean?,
        val dokumenter: List<Dokumenter>?,
    )

    data class Dokumenter(
        val dokumentInfoId: String?,
        val tittel: String?,
    )
}
