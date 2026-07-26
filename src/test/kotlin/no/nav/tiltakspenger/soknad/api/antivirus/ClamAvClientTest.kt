package no.nav.tiltakspenger.soknad.api.antivirus

import arrow.core.nonEmptyListOf
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import org.junit.jupiter.api.Test

internal class ClamAvClientTest {
    private val pdfVedlegg = Vedlegg(filnavn = "søknad.pdf", contentType = "application/pdf", dokument = byteArrayOf(0x25, 0x50, 0x44, 0x46))
    private val pngVedlegg = Vedlegg(filnavn = "bilde.png", contentType = "image/png", dokument = byteArrayOf(0x89.toByte(), 0x50, 0xFF.toByte()))

    @Test
    fun `skanner vedlegg med default HttpKlient-oppsett`() {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                post(urlEqualTo("/scan")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""[{"Filename":"søknad.pdf","Result":"OK"}]"""),
                ),
            )

            runTest {
                val resultat = ClamAvClient(avEndpoint = "${wiremock.baseUrl()}/scan", clock = fixedClock)
                    .scan(nonEmptyListOf(pdfVedlegg))
                    .getOrFail()

                resultat shouldBe listOf(AvSjekkResultat(filnavn = "søknad.pdf", resultat = Status.OK))
                // At WireMock klarer å parse delen, beviser at multipart-framingen er gyldig for en ekte server.
                val mottattDel = wiremock.findAll(postRequestedFor(urlEqualTo("/scan"))).single().parts!!.single()
                mottattDel.name shouldBe "file0"
                mottattDel.body.asBytes().toList() shouldBe pdfVedlegg.dokument.toList()
            }
        }
    }

    @Test
    fun `hvert vedlegg blir sin egen multipart-del med filnavn og content-type`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(
            """[{"Filename":"søknad.pdf","Result":"OK"},{"Filename":"bilde.png","Result":"FOUND"}]""",
        )

        val resultat = ClamAvClient(avEndpoint = "http://clamav/scan", clock = fixedClock, transport = transport)
            .scan(nonEmptyListOf(pdfVedlegg, pngVedlegg))
            .getOrFail()

        resultat shouldBe listOf(
            AvSjekkResultat(filnavn = "søknad.pdf", resultat = Status.OK),
            AvSjekkResultat(filnavn = "bilde.png", resultat = Status.FOUND),
        )

        val kall = transport.mottatteKall.single()
        kall.metode shouldBe "POST"
        kall.uri.toString() shouldBe "http://clamav/scan"
        kall.request.headers().firstValue("Content-Type").get() shouldContain "multipart/form-data; boundary="
        // ClamAV er en plattformtjeneste uten autentisering.
        kall.request.headers().firstValue("Authorization").isPresent shouldBe false

        // Delenes headere skrives som UTF-8, så norske tegn i filnavn må dekodes som UTF-8 for å kunne assertes.
        val body = String(kall.bodyBytes, Charsets.UTF_8)
        body shouldContain """name="file0"; filename="søknad.pdf""""
        body shouldContain "Content-Type: application/pdf"
        body shouldContain """name="file1"; filename="bilde.png""""
        body shouldContain "Content-Type: image/png"
    }

    @Test
    fun `feilstatus fra clamav gir Left`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503, body = "clamav er nede")

        val feil = ClamAvClient(avEndpoint = "http://clamav/scan", clock = fixedClock, transport = transport)
            .scan(nonEmptyListOf(pdfVedlegg))
            .leftOrNull()!!

        feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 503
    }

    @Test
    fun `retryer ikke, som før migreringen`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503, body = "clamav er nede")

        ClamAvClient(avEndpoint = "http://clamav/scan", clock = fixedClock, transport = transport).scan(nonEmptyListOf(pdfVedlegg))

        transport.mottatteKall.size shouldBe 1
    }
}
