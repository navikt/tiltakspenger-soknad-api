package no.nav.tiltakspenger.soknad.api.testutils

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import java.io.ByteArrayOutputStream

/**
 * En ekte, minimal PDF.
 * Vi genererer den i stedet for å bruke oppdiktede bytes, fordi både Tika-gjenkjenningen og pdfbox skal kjøre for ekte i testene.
 */
fun enkelPdf(antallSider: Int = 1): ByteArray {
    PDDocument().use { dokument ->
        repeat(antallSider) { dokument.addPage(PDPage()) }
        val baos = ByteArrayOutputStream()
        dokument.save(baos)
        return baos.toByteArray()
    }
}
