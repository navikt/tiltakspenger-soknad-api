package no.nav.tiltakspenger.soknad.api.dokarkiv

import io.github.oshai.kotlinlogging.KotlinLogging
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
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.soknad.api.httpClientWithRetry
import no.nav.tiltakspenger.soknad.api.objectMapper
import no.nav.tiltakspenger.soknad.api.pdl.INDIVIDSTONAD

// https://confluence.adeo.no/display/BOA/opprettJournalpost
// swagger: https://dokarkiv-q2.dev.intern.nav.no/swagger-ui/index.html#/

internal const val DOKARKIV_PATH = "rest/journalpostapi/v1/journalpost"

class DokarkivClient(
    private val client: HttpClient = httpClientWithRetry(timeout = 30L),
    private val baseUrl: String,
    private val getToken: suspend () -> AccessToken,
) {
    private val log = KotlinLogging.logger {}

    suspend fun opprettJournalpost(
        request: JournalpostRequest,
        søknadId: SøknadId,
        callId: String,
    ): String {
        try {
            log.info { "Henter credentials for å arkivere i dokarkiv" }
            val token = getToken().token
            log.info { "Hent credentials til arkiv OK. Starter journalføring av søknad" }
            val res = client.post("$baseUrl/$DOKARKIV_PATH") {
                accept(ContentType.Application.Json)
                header("X-Correlation-ID", INDIVIDSTONAD)
                header("Nav-Callid", callId)
                parameter("forsoekFerdigstill", request.kanFerdigstilleAutomatisk())
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(objectMapper.writeValueAsString(request))
            }
            val response = res.call.body<DokarkivResponse>()
            log.info { "Vi har opprettet journalpost med id: ${response.journalpostId} for søknad $søknadId" }

            if (request.kanFerdigstilleAutomatisk() && !response.journalpostferdigstilt) {
                throw IllegalStateException("DokarkivClient: Journalpost ${response.journalpostId} for søknad $søknadId ble opprettet, men ikke ferdigstilt")
            }
            return response.journalpostId
        } catch (throwable: Throwable) {
            if (throwable is ClientRequestException && throwable.response.status == HttpStatusCode.Conflict) {
                val response = throwable.response.call.body<DokarkivResponse>()
                log.info { "Søknad med id $søknadId har allerede blitt journalført (409 Conflict) med journalpostId ${response.journalpostId}" }
                return response.journalpostId
            }
            if (throwable is IllegalStateException) {
                throw RuntimeException("DokarkivClient: Fikk en IllegalStateException", throwable)
            } else {
                throw RuntimeException("DokarkivClient: Fikk en ukjent exception.", throwable)
            }
        }
    }

    data class DokarkivResponse(
        val journalpostId: String,
        val journalpostferdigstilt: Boolean,
    )
}
