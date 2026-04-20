package no.nav.tiltakspenger.soknad.api.dokarkiv

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.soknad.api.httpClientGeneric
import no.nav.tiltakspenger.soknad.api.soknad.validering.søknad
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import org.junit.jupiter.api.Test
import java.time.Instant

internal class DokarkivClientTest {
    private val journalpostId = JournalpostId("1")
    private val søknadId = SøknadId.random()
    private val baseurl = "http://dokarkiv"

    @Test
    fun `opprettJournalpost - skal ikke ferdigstille, blir opprettet - returnerer journalpostid`() {
        val mock = MockEngine {
            respond(
                content = svarIkkeFerdigstilt,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = httpClientGeneric(mock)
        val dokarkivClient = DokarkivClient(
            client = client,
            baseUrl = baseurl,
        ) { getMockToken() }

        runTest {
            val resp = dokarkivClient.opprettJournalpost(
                request = journalpostRequest(),
                søknadId = søknadId,
                callId = "123",
            )

            resp shouldBe journalpostId
        }
    }

    @Test
    fun `opprettJournalpost - skal ferdigstille, blir opprettet og ferdigstilt - returnerer journalpostid`() {
        val mock = MockEngine {
            respond(
                content = svarFerdigstilt,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = httpClientGeneric(mock)
        val dokarkivClient = DokarkivClient(
            client = client,
            baseUrl = baseurl,
        ) { getMockToken() }

        runTest {
            val resp = dokarkivClient.opprettJournalpost(
                request = journalpostRequest("saksnummer"),
                søknadId = søknadId,
                callId = "123",
            )

            resp shouldBe journalpostId
        }
    }

    @Test
    fun `opprettJournalpost - skal ferdigstille, blir opprettet men ikke ferdigstilt - kaster feil`() {
        val mock = MockEngine {
            respond(
                content = svarIkkeFerdigstilt,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = httpClientGeneric(mock)
        val dokarkivClient = DokarkivClient(
            client = client,
            baseUrl = baseurl,
        ) { getMockToken() }

        runTest {
            shouldThrow<RuntimeException> {
                dokarkivClient.opprettJournalpost(
                    request = journalpostRequest("saksnummer"),
                    søknadId = søknadId,
                    callId = "123",
                )
            }
        }
    }

    @Test
    fun `hvis dokarkiv svarer med 409 Conflict returnerer opprettJournalpost en journalpostId hvis vi har fått en`() {
        val mock = MockEngine {
            respond(
                content = svarIkkeFerdigstilt,
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = httpClientGeneric(mock)
        val dokarkivClient = DokarkivClient(
            client = client,
            baseUrl = baseurl,
        ) { getMockToken() }

        runTest {
            val resp = dokarkivClient.opprettJournalpost(
                request = journalpostRequest(),
                søknadId = søknadId,
                callId = "123",
            )
            resp shouldBe journalpostId
        }
    }

    @Test
    fun `dokarkiv svarer med feil`() {
        val mock = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = httpClientGeneric(mock)
        val dokarkivClient = DokarkivClient(
            client = client,
            baseUrl = baseurl,
        ) { getMockToken() }

        runTest {
            shouldThrow<RuntimeException> {
                dokarkivClient.opprettJournalpost(
                    request = journalpostRequest(),
                    søknadId = søknadId,
                    callId = "123",
                )
            }
        }
    }

    private fun getMockToken(): AccessToken {
        return AccessToken("token", Instant.now().plusSeconds(3600)) {}
    }

    private fun journalpostRequest(
        saksnummer: String? = null,
    ) = JournalpostRequest.from(
        fnr = "ident",
        søknad = søknad(),
        pdf = "dette er pdf innholdet".toByteArray(),
        vedlegg = listOf(
            Vedlegg(
                filnavn = "filnavnVedlegg",
                contentType = "application/pdf",
                dokument = "vedleggInnhold".toByteArray(),
            ),
        ),
        saksnummer = saksnummer,
    )

    private val svarFerdigstilt = """
        {
          "journalpostId": "$journalpostId",
          "journalpostferdigstilt": true
        }
    """.trimIndent()

    private val svarIkkeFerdigstilt = """
        {
          "journalpostId": "$journalpostId",
          "journalpostferdigstilt": false
        }
    """.trimIndent()
}
