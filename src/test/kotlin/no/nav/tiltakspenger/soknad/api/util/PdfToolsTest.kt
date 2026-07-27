package no.nav.tiltakspenger.soknad.api.util

import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.tiltakspenger.soknad.api.testutils.enkelPdf
import no.nav.tiltakspenger.soknad.api.util.Detect.detect
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO

class PdfToolsTest {
    @Test
    fun `konverterPdfTilBilder gir ett png-bilde per side`() {
        val bilder = PdfTools.konverterPdfTilBilder(enkelPdf(antallSider = 2))

        bilder.size shouldBe 2
        bilder.forEach { bilde ->
            bilde.type shouldBe Detect.IMAGE_PNG
            bilde.data.detect() shouldBe Detect.IMAGE_PNG
            bilde.data.size shouldBeGreaterThan 0
        }
    }

    /**
     * Låser oppløsningen vi faktisk rendrer på.
     * `PDFRenderer.renderImage(side)` bruker PDFBox sin default-skala 1,0, altså 72 dpi — en A4-side blir da 595×841 px.
     * Testen finnes for at et bytte til [org.apache.pdfbox.rendering.PDFRenderer.renderImageWithDPI] skal bli et bevisst valg og ikke en stille endring.
     * Se navikt/tiltakspenger-soknad-api#865.
     */
    @Test
    fun `konverterPdfTilBilder rendrer på 72 dpi`() {
        val a4 = PDDocument().use { dokument ->
            dokument.addPage(PDPage(PDRectangle.A4))
            val baos = ByteArrayOutputStream()
            dokument.save(baos)
            baos.toByteArray()
        }

        val bilde = ImageIO.read(PdfTools.konverterPdfTilBilder(a4).single().data.inputStream())

        bilde.width shouldBe 595
        bilde.height shouldBe 841
    }

    /**
     * Rastreringen er tilsiktet — den flater ut skjemafelter som ellers ville vært redigerbare i arkivet.
     * Prisen er at tekstlaget forsvinner, så dokumentet ikke lenger kan søkes i, merkes eller leses av en skjermleser.
     * Begge halvdelene av den avveiningen står her, siden det er dem #865 skal ta stilling til.
     */
    @Test
    fun `rastrering flater ut skjemafelter, men fjerner samtidig tekstlaget`() {
        val original = skjemaPdf()
        tekstI(original) shouldContain "Fast tekst i dokumentet"
        antallSkjemafelter(original) shouldBe 1

        val rastrert = PdfTools.slåSammenPdfer(
            nonEmptyListOf(pdfMedBilde(PdfTools.konverterPdfTilBilder(original).single().data)),
        )

        antallSkjemafelter(rastrert) shouldBe 0
        tekstI(rastrert) shouldNotContain "Fast tekst i dokumentet"
    }

    @Test
    fun `slåSammenPdfer gir én pdf med alle sidene`() {
        val sammenslått = PdfTools.slåSammenPdfer(nonEmptyListOf(enkelPdf(1), enkelPdf(2)))

        sammenslått.detect() shouldBe Detect.APPLICATON_PDF
        Loader.loadPDF(sammenslått).use { dokument ->
            dokument.numberOfPages shouldBe 3
        }
    }

    @Test
    fun `slåSammenPdfer beholder innholdet fra hver kilde`() {
        val sammenslått = PdfTools.slåSammenPdfer(
            nonEmptyListOf(pdfMedTekst("Første dokument"), pdfMedTekst("Andre dokument")),
        )

        tekstI(sammenslått).let {
            it shouldContain "Første dokument"
            it shouldContain "Andre dokument"
        }
    }

    @Test
    fun `slåSammenPdfer med én kilde gir den samme pdf-en tilbake`() {
        val sammenslått = PdfTools.slåSammenPdfer(nonEmptyListOf(pdfMedTekst("Alene")))

        Loader.loadPDF(sammenslått).use { it.numberOfPages shouldBe 1 }
        tekstI(sammenslått) shouldContain "Alene"
    }

    /**
     * Tika gjenkjenner en fil på signaturen, så en avkortet eller ødelagt fil som starter med `%PDF-` passerer [sjekkContentType].
     * Da er det pdfbox som oppdager det, og den kaster.
     * Feilen er ikke modellert som [arrow.core.Either] noe sted i konverteringsstien, så kalleren må være klar over den.
     */
    @Test
    fun `korrupt pdf kaster IOException fra pdfbox`() {
        val korrupt = "%PDF-1.4\nikke en ekte pdf".toByteArray()
        korrupt.detect() shouldBe Detect.APPLICATON_PDF

        shouldThrow<IOException> { PdfTools.konverterPdfTilBilder(korrupt) }
        shouldThrow<IOException> { PdfTools.slåSammenPdfer(nonEmptyListOf(korrupt)) }
    }

    private fun tekstI(pdf: ByteArray): String = Loader.loadPDF(pdf).use { PDFTextStripper().getText(it) }

    private fun antallSkjemafelter(pdf: ByteArray): Int =
        Loader.loadPDF(pdf).use { it.documentCatalog.acroForm?.fields?.size ?: 0 }

    private fun pdfMedTekst(tekst: String): ByteArray = PDDocument().use { dokument ->
        val side = PDPage()
        dokument.addPage(side)
        PDPageContentStream(dokument, side).use { innhold ->
            innhold.beginText()
            innhold.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
            innhold.newLineAtOffset(72f, 700f)
            innhold.showText(tekst)
            innhold.endText()
        }
        val baos = ByteArrayOutputStream()
        dokument.save(baos)
        baos.toByteArray()
    }

    private fun pdfMedBilde(png: ByteArray): ByteArray = PDDocument().use { dokument ->
        val side = PDPage()
        dokument.addPage(side)
        val bilde = org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(dokument, png, "side")
        PDPageContentStream(dokument, side).use { it.drawImage(bilde, 0f, 0f, side.mediaBox.width, side.mediaBox.height) }
        val baos = ByteArrayOutputStream()
        dokument.save(baos)
        baos.toByteArray()
    }

    /** En pdf med både fast tekst og et utfylt, redigerbart skjemafelt — altså det rastreringen er der for å bli kvitt. */
    private fun skjemaPdf(): ByteArray = PDDocument().use { dokument ->
        val side = PDPage()
        dokument.addPage(side)
        PDPageContentStream(dokument, side).use { innhold ->
            innhold.beginText()
            innhold.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
            innhold.newLineAtOffset(72f, 700f)
            innhold.showText("Fast tekst i dokumentet")
            innhold.endText()
        }

        val acroForm = PDAcroForm(dokument)
        dokument.documentCatalog.acroForm = acroForm
        acroForm.defaultResources = PDResources().apply {
            put(COSName.getPDFName("Helv"), PDType1Font(Standard14Fonts.FontName.HELVETICA))
        }
        acroForm.defaultAppearance = "/Helv 12 Tf 0 g"

        val felt = PDTextField(acroForm)
        felt.partialName = "navn"
        acroForm.fields.add(felt)
        felt.widgets.first().also { widget ->
            widget.rectangle = PDRectangle(72f, 600f, 200f, 20f)
            widget.page = side
            side.annotations.add(widget)
        }
        felt.value = "Utfylt feltverdi"

        val baos = ByteArrayOutputStream()
        dokument.save(baos)
        baos.toByteArray()
    }
}
