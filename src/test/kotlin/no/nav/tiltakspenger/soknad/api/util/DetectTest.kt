package no.nav.tiltakspenger.soknad.api.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.soknad.api.util.Detect.detect
import no.nav.tiltakspenger.soknad.api.util.Detect.isImage
import no.nav.tiltakspenger.soknad.api.util.Detect.isJpeg
import no.nav.tiltakspenger.soknad.api.util.Detect.isPdf
import no.nav.tiltakspenger.soknad.api.util.Detect.isPng
import org.junit.jupiter.api.Test

class DetectTest {

    private val pngBytes = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    )
    private val jpegBytes = byteArrayOf(
        0xFF.toByte(),
        0xD8.toByte(),
        0xFF.toByte(),
        0xE0.toByte(),
        0x00,
        0x10,
    ) + "JFIF".toByteArray()
    private val pdfBytes = "%PDF-1.4\n%%EOF".toByteArray()
    private val tekstBytes = "bare tekst".toByteArray()

    @Test
    fun `detect gjenkjenner png, jpeg og pdf fra bytearray og inputstream`() {
        pngBytes.detect() shouldBe Detect.IMAGE_PNG
        jpegBytes.detect() shouldBe Detect.IMAGE_JPEG
        pdfBytes.detect() shouldBe Detect.APPLICATON_PDF
        pngBytes.inputStream().detect() shouldBe Detect.IMAGE_PNG
    }

    @Test
    fun `isPng, isJpeg og isPdf treffer riktig filtype`() {
        pngBytes.isPng() shouldBe true
        jpegBytes.isJpeg() shouldBe true
        pdfBytes.isPdf() shouldBe true
        pdfBytes.isPng() shouldBe false

        pngBytes.inputStream().isPng() shouldBe true
        jpegBytes.inputStream().isJpeg() shouldBe true
        pdfBytes.inputStream().isPdf() shouldBe true
    }

    @Test
    fun `isImage er sann for png og jpeg, usann for pdf`() {
        pngBytes.isImage() shouldBe true
        jpegBytes.isImage() shouldBe true
        pdfBytes.isImage() shouldBe false
        // For InputStream konsumerer isJpeg-sjekken strømmen, så kun jpeg (første sjekk) kan bli sann.
        jpegBytes.inputStream().isImage() shouldBe true
        pdfBytes.inputStream().isImage() shouldBe false
    }

    @Test
    fun `liste av bytearrays er pdf kun når alle er pdf og lista ikke er tom`() {
        listOf(pdfBytes, pdfBytes).isPdf() shouldBe true
        listOf(pdfBytes, pngBytes).isPdf() shouldBe false
        emptyList<ByteArray>().isPdf() shouldBe false
    }

    @Test
    fun `sjekkContentType godtar godkjente filtyper og kaster på andre`() {
        sjekkContentType(pngBytes) shouldBe Detect.IMAGE_PNG
        shouldThrow<UnsupportedContentException> { sjekkContentType(tekstBytes) }
    }
}
