package no.nav.tiltakspenger.soknad.api.joark

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.config.ApplicationConfig
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.soknad.api.httpClientGeneric
import no.nav.tiltakspenger.soknad.api.soknad.validering.søknad
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import org.junit.jupiter.api.Test

internal class JoarkClientTest {
    private val journalpostId = "1"

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `joark svarer alt ok`() {
        val mock = MockEngine {
            respond(
                content = okSvarJoark,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = httpClientGeneric(mock)
        val mockJoarkCredentialsClient = mockk<JoarkCredentialsClient>()
        coEvery { mockJoarkCredentialsClient.getToken() } returns "token"
        val config = ApplicationConfig("application.test.conf")
        val joarkClient = JoarkClient(
            config = config,
            client = client,
            joarkCredentialsClient = mockJoarkCredentialsClient,
        )

        runTest {
            val resp = joarkClient.opprettJournalpost(
                dokumentInnhold = dokument,
                callId = "test",
            )

            resp shouldBe journalpostId
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `joark svarer ikke ferdigstilt`() {
        val mock = MockEngine {
            respond(
                content = svarIkkeFerdigstiltJoark,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val mockJoarkCredentialsClient = mockk<JoarkCredentialsClient>()
        coEvery { mockJoarkCredentialsClient.getToken() } returns "token"

//        val joarkResonse = JoarkClient.JoarkResponse(
//            journalpostId = journalpostId,
//            journalpostferdigstilt = false,
//            dokumenter = listOf(dokumentResponse),
//        )

        val client = httpClientGeneric(mock)
        val config = ApplicationConfig("application.test.conf")
        val joarkClient = JoarkClient(
            config = config,
            client = client,
            joarkCredentialsClient = mockJoarkCredentialsClient,
        )

        runTest {
            val resp = joarkClient.opprettJournalpost(
                dokumentInnhold = dokument,
                callId = "test",
            )

            resp shouldBe journalpostId
        }
    }

    @Test
    fun `hvis joark svarer med 409 Conflict returnerer opprettJournalpost en journalpostId hvis vi har fått en`() {
        val mock = MockEngine {
            respond(
                content = okSvarJoark,
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val mockJoarkCredentialsClient = mockk<JoarkCredentialsClient>()
        coEvery { mockJoarkCredentialsClient.getToken() } returns "token"

        val client = httpClientGeneric(mock)
        val config = ApplicationConfig("application.test.conf")
        val joarkClient = JoarkClient(
            config = config,
            client = client,
            joarkCredentialsClient = mockJoarkCredentialsClient,
        )

        runTest {
            val resp = joarkClient.opprettJournalpost(
                dokumentInnhold = dokument,
                callId = "test",
            )
            resp shouldBe journalpostId
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `joark svarer med feil`() {
        val mock = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val mockJoarkCredentialsClient = mockk<JoarkCredentialsClient>()
        coEvery { mockJoarkCredentialsClient.getToken() } returns "token"

        val client = httpClientGeneric(mock)
        val config = ApplicationConfig("application.test.conf")
        val joarkClient = JoarkClient(
            config = config,
            client = client,
            joarkCredentialsClient = mockJoarkCredentialsClient,
        )

        runTest {
            shouldThrow<RuntimeException> {
                joarkClient.opprettJournalpost(
                    dokumentInnhold = dokument,
                    callId = "test",
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `joark svarer med null i ferdigstilt`() {
        val mock = MockEngine {
            respond(
                content = svarNullIFerdigstiltJoark,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val mockJoarkCredentialsClient = mockk<JoarkCredentialsClient>()
        coEvery { mockJoarkCredentialsClient.getToken() } returns "token"

//        val joarkResonse = JoarkClient.JoarkResponse(
//            journalpostId = journalpostId,
//            journalpostferdigstilt = null,
//            dokumenter = listOf(dokumentResponse),
//        )

        val client = httpClientGeneric(mock)
        val config = ApplicationConfig("application.test.conf")
        val joarkClient = JoarkClient(
            config = config,
            client = client,
            joarkCredentialsClient = mockJoarkCredentialsClient,
        )

        runTest {
            val resp = joarkClient.opprettJournalpost(
                dokumentInnhold = dokument,
                callId = "test",
            )

            resp shouldBe journalpostId
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `joark svarer uten journalpostid`() {
        val mock = MockEngine {
            respond(
                content = svarUtenJournalpostId,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val mockJoarkCredentialsClient = mockk<JoarkCredentialsClient>()
        coEvery { mockJoarkCredentialsClient.getToken() } returns "token"

        val joarkResonse = JoarkClient.JoarkResponse(
            journalpostId = null,
            journalpostferdigstilt = null,
            dokumenter = listOf(dokumentResponse),
        )

        val client = httpClientGeneric(mock)
        val config = ApplicationConfig("application.test.conf")
        val joarkClient = JoarkClient(
            config = config,
            client = client,
            joarkCredentialsClient = mockJoarkCredentialsClient,
        )

        runTest {
            shouldThrow<IllegalStateException> {
                joarkClient.opprettJournalpost(
                    dokumentInnhold = dokument,
                    callId = "test",
                )
            }.message shouldBe "Kallet til Joark gikk ok, men vi fikk ingen journalpostId fra Joark"
        }
    }

    private val dokument = Journalpost.Søknadspost.from(
        fnr = "ident",
        søknadDTO = søknad(),
        pdf = "dette er pdf innholdet".toByteArray(),
        vedlegg = listOf(
            Vedlegg(
                filnavn = "filnavnVedlegg",
                contentType = "application/pdf",
                dokument = "vedleggInnhold".toByteArray(),
            ),
        ),
    )

    private val dokumentInfoId = "485227498"
    private val dokumentInfoVedleggId = "485227499"
    private val dokumentTittel = "Søknad om tiltakspenger"
    private val dokumentVedleggFilnavn = "filnavnVedlegg"
    private val dokumentResponse = JoarkClient.Dokumenter(
        dokumentInfoId = dokumentInfoId,
        tittel = dokumentTittel,

    )
    private val okSvarJoark = """
        {
          "journalpostId": "$journalpostId",
          "journalpostferdigstilt": true,
          "dokumenter": [
            {
              "dokumentInfoId": "$dokumentInfoId",
              "tittel": "$dokumentTittel"
            },
            {
              "dokumentInfoId": "$dokumentInfoVedleggId",
              "tittel": "$dokumentVedleggFilnavn"
            }
          ]
        }
    """.trimIndent()

    private val svarIkkeFerdigstiltJoark = """
        {
          "journalpostId": "$journalpostId",
          "journalpostferdigstilt": false,
          "dokumenter": [
            {
              "dokumentInfoId": "$dokumentInfoId",
              "tittel": "$dokumentTittel"
            }
          ]
        }
    """.trimIndent()

    private val svarNullIFerdigstiltJoark = """
        {
          "journalpostId": "$journalpostId",
          "journalpostferdigstilt": null,
          "dokumenter": [
            {
              "dokumentInfoId": "$dokumentInfoId",
              "tittel": "$dokumentTittel"
            }
          ]
        }
    """.trimIndent()

    private val svarUtenJournalpostId = """
        {
          "journalpostId": null,
          "journalpostferdigstilt": null,
          "dokumenter": [
            {
              "dokumentInfoId": "$dokumentInfoId",
              "tittel": "$dokumentTittel"
            }
          ]
        }
    """.trimIndent()
}
