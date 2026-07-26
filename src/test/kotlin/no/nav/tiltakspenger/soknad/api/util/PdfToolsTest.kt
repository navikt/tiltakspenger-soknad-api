package no.nav.tiltakspenger.soknad.api.util

import arrow.core.nonEmptyListOf
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.soknad.api.testutils.enkelPdf
import no.nav.tiltakspenger.soknad.api.util.Detect.isPdf
import no.nav.tiltakspenger.soknad.api.util.Detect.isPng
import org.apache.pdfbox.Loader
import org.junit.jupiter.api.Test

class PdfToolsTest {
    @Test
    fun `konverterPdfTilBilder gir ett png-bilde per side`() {
        val bilder = PdfTools.konverterPdfTilBilder(enkelPdf(antallSider = 2))

        bilder.size shouldBe 2
        bilder.forEach { bilde ->
            bilde.type shouldBe Detect.IMAGE_PNG
            bilde.data.isPng() shouldBe true
            bilde.data.size shouldBeGreaterThan 0
        }
    }

    @Test
    fun `slåSammenPdfer gir én pdf med alle sidene`() {
        val sammenslått = PdfTools.slåSammenPdfer(nonEmptyListOf(enkelPdf(1), enkelPdf(2)))

        sammenslått.isPdf() shouldBe true
        Loader.loadPDF(sammenslått).use { dokument ->
            dokument.numberOfPages shouldBe 3
        }
    }
}
