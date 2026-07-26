package no.nav.tiltakspenger.soknad.api.pdf

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.soknad.api.soknad.validering.søknad
import no.nav.tiltakspenger.soknad.api.util.Detect.IMAGE_JPEG
import no.nav.tiltakspenger.soknad.api.util.Detect.IMAGE_PNG
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

internal class PdfClientTest {
    private val pdf = "dette er innholdet i pdf vi får tilbake fra pdfGen".toByteArray()

    private fun klient(
        transport: HttpTransport,
        isLocalOrDev: Boolean = false,
        pdfEndpoint: String = "http://pdf",
    ) = PdfClient(
        pdfEndpoint = pdfEndpoint,
        pdfgenrsEndpoint = "http://pdfgenrs",
        isLocalOrDev = isLocalOrDev,
        clock = fixedClock,
        transport = transport,
    )

    @Test
    fun `genererer søknadspdf med default HttpKlient-oppsett`() {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(
                    com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/$PDFGEN_PATH/$SOKNAD_TEMPLATE"),
                ).willReturn(
                    com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(pdf),
                ),
            )

            runTest {
                val klient = PdfClient(
                    pdfEndpoint = wiremock.baseUrl(),
                    pdfgenrsEndpoint = "http://pdfgenrs",
                    isLocalOrDev = false,
                    clock = fixedClock,
                )

                val (hovedPdf, skyggePdf) = klient.genererPdf(søknad()).getOrFail()

                hovedPdf.toList() shouldBe pdf.toList()
                skyggePdf shouldBe null
            }
        }
    }

    @Test
    fun `genererer også skygge-pdf fra pdfgenrs i local og dev`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøBytes(pdf, contentType = "application/pdf")
        transport.leggIKøBytes(pdf, contentType = "application/pdf")

        val (hovedPdf, skyggePdf) = klient(transport, isLocalOrDev = true).genererPdf(søknad()).getOrFail()

        hovedPdf.toList() shouldBe pdf.toList()
        skyggePdf!!.toList() shouldBe pdf.toList()
        transport.mottatteKall.map { it.uri.host }.toSet() shouldBe setOf("pdf", "pdfgenrs")
    }

    @Test
    fun `sender søknaden som JSON og ber om pdf tilbake`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøBytes(pdf, contentType = "application/pdf")

        klient(transport).genererPdf(søknad()).getOrFail()

        val kall = transport.mottatteKall.single()
        kall.uri.toString() shouldBe "http://pdf/$PDFGEN_PATH/$SOKNAD_TEMPLATE"
        kall.request.headers().firstValue("Content-Type").get() shouldBe "application/json"
        kall.request.headers().firstValue("Accept").get() shouldBe "application/pdf"
        kall.request.headers().firstValue("X-Correlation-ID").isPresent shouldBe true
        kall.bodyTekst shouldContain "personopplysninger"
    }

    @Test
    fun `feil fra pdfgen gir Left`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(404, body = "finnes ikke")

        val feil = klient(transport).genererPdf(søknad()).leftOrNull()!!

        feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 404
    }

    @Test
    fun `feiler skygge-kallet, feiler hele genereringen i local og dev`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøBytes(pdf, contentType = "application/pdf")
        transport.leggIKøStatus(500, body = "pdfgenrs er nede")

        klient(transport, isLocalOrDev = true).genererPdf(søknad()).leftOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 500
    }

    @Test
    fun `png-vedlegg konverteres til pdf via bilde-endepunktet`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøBytes(pdf, contentType = "application/pdf")

        val konvertert = klient(transport).konverterVedlegg(listOf(vedlegg(pngBytes(), IMAGE_PNG))).getOrFail().single()

        konvertert.filnavn shouldBe "$" + "bilde.png-konvertert.pdf"
        konvertert.contentType shouldBe "application/pdf"
        konvertert.dokument.toList() shouldBe pdf.toList()

        val kall = transport.mottatteKall.single()
        kall.uri.toString() shouldBe "http://pdf/$PDFGEN_IMAGE_PATH"
        kall.request.headers().firstValue("Content-Type").get() shouldBe IMAGE_PNG
        kall.request.headers().firstValue("Accept").get() shouldBe "application/pdf"
        kall.bodyBytes.toList() shouldBe pngBytes().toList()
    }

    @Test
    fun `jpeg-vedlegg sendes med sin egen Content-Type`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøBytes(pdf, contentType = "application/pdf")

        klient(transport).konverterVedlegg(listOf(vedlegg(jpegBytes(), IMAGE_JPEG))).getOrFail()

        transport.mottatteKall.single().request.headers().firstValue("Content-Type").get() shouldBe IMAGE_JPEG
    }

    @Test
    fun `pdf-vedlegg går veien om ett bilde-kall per side`() = runTest {
        val transport = FakeHttpTransport()
        // Bildekallene må svare med ekte pdf-er, siden sidene slås sammen igjen etterpå.
        repeat(2) { transport.leggIKøBytes(enkelPdf(antallSider = 1), contentType = "application/pdf") }

        val konvertert = klient(transport)
            .konverterVedlegg(listOf(vedlegg(enkelPdf(antallSider = 2), "application/pdf")))
            .getOrFail()
            .single()

        // Filnavnet beholdes for pdf-vedlegg, i motsetning til bilde-vedlegg.
        konvertert.filnavn shouldBe "bilde.png"
        transport.mottatteKall.size shouldBe 2
        transport.mottatteKall.forEach { it.request.headers().firstValue("Content-Type").get() shouldBe IMAGE_PNG }
    }

    @Test
    fun `vedlegg med ugyldig filformat gir UgyldigFilformat`() = runTest {
        val feil = klient(FakeHttpTransport())
            .konverterVedlegg(listOf(vedlegg("ikke et bilde".toByteArray(), "text/plain")))
            .leftOrNull()!!

        feil shouldBe KunneIkkeKonvertereVedlegg.UgyldigFilformat("text/plain")
    }

    @Test
    fun `feil fra bilde-endepunktet gir KallFeilet`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(500, body = "pdfgen er nede")

        val feil = klient(transport).konverterVedlegg(listOf(vedlegg(pngBytes(), IMAGE_PNG))).leftOrNull()!!

        feil.shouldBeInstanceOf<KunneIkkeKonvertereVedlegg.KallFeilet>()
            .feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 500
    }

    @Test
    fun `bildebytene går uendret på wire`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøBytes(pdf, contentType = "application/pdf")

        klient(transport).konverterVedlegg(listOf(vedlegg(pngBytes(), IMAGE_PNG))).getOrFail()

        // Selve placeholder-oppførselen i rawRequestString er verifisert i libs; her sjekker vi at klienten ikke gjør noe med bytene.
        transport.mottatteKall.single().bodyBytes.toList() shouldBe pngBytes().toList()
    }

    private fun vedlegg(dokument: ByteArray, contentType: String) =
        Vedlegg(filnavn = "bilde.png", contentType = contentType, dokument = dokument)

    private fun pngBytes(): ByteArray {
        val bilde = java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val baos = ByteArrayOutputStream()
        javax.imageio.ImageIO.write(bilde, "png", baos)
        return baos.toByteArray()
    }

    private fun jpegBytes(): ByteArray {
        val bilde = java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val baos = ByteArrayOutputStream()
        javax.imageio.ImageIO.write(bilde, "jpeg", baos)
        return baos.toByteArray()
    }

    private fun enkelPdf(antallSider: Int): ByteArray {
        PDDocument().use { dokument ->
            repeat(antallSider) { dokument.addPage(PDPage()) }
            val baos = ByteArrayOutputStream()
            dokument.save(baos)
            return baos.toByteArray()
        }
    }
}
