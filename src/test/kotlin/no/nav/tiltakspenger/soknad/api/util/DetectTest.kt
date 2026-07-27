package no.nav.tiltakspenger.soknad.api.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.soknad.api.util.Detect.detect
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
    fun `detect gjenkjenner png, jpeg og pdf`() {
        pngBytes.detect() shouldBe Detect.IMAGE_PNG
        jpegBytes.detect() shouldBe Detect.IMAGE_JPEG
        pdfBytes.detect() shouldBe Detect.APPLICATON_PDF
    }

    @Test
    fun `detect gjenkjenner innhold som ikke er en godkjent filtype`() {
        tekstBytes.detect() shouldBe "text/plain"
    }

    @Test
    fun `sjekkContentType godtar godkjente filtyper og kaster på andre`() {
        sjekkContentType(pngBytes) shouldBe Detect.IMAGE_PNG
        sjekkContentType(jpegBytes) shouldBe Detect.IMAGE_JPEG
        sjekkContentType(pdfBytes) shouldBe Detect.APPLICATON_PDF
        shouldThrow<UnsupportedContentException> { sjekkContentType(tekstBytes) }
    }
}
