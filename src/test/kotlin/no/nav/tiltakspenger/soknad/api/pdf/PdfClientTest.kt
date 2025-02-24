package no.nav.tiltakspenger.soknad.api.pdf

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.soknad.api.httpClientGeneric
import no.nav.tiltakspenger.soknad.api.soknad.validering.søknad
import org.junit.jupiter.api.Test

internal class PdfClientTest {

    @Test
    fun `får tilbake en pdf hvis alt går ok`() {
        val pdf = "dette er innholdet i pdf vi får tilbake fra pdfGen".toByteArray()
        val mock = MockEngine {
            respond(
                content = pdf,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = httpClientGeneric(mock)
        val pdfClient = PdfClient(
            pdfEndpoint = "http://pdf",
            client = client,
        )

        runTest {
            val resp = pdfClient.genererPdf(søknad())

            resp shouldBe pdf
        }
    }

    @Test
    fun `kaster en feil hvis generering av pdf ikke går ok`() {
        val pdf = "".toByteArray()
        val mock = MockEngine {
            respond(
                content = pdf,
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = httpClientGeneric(mock)
        val pdfClient = PdfClient(
            pdfEndpoint = "http://pdf",
            client = client,
        )

        runTest {
            shouldThrow<RuntimeException> {
                pdfClient.genererPdf(søknad())
            }
        }
    }
}
