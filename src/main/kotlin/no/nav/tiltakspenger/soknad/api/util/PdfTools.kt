package no.nav.tiltakspenger.soknad.api.util

import arrow.core.Nel
import arrow.core.toNonEmptyListOrNull
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

// WIP

object PdfTools {
    /** En PDF har alltid minst én side, så resultatet er en [Nel] — det lar [slåSammenPdfer] slippe å håndtere det tomme tilfellet. */
    fun konverterPdfTilBilder(pdfByteArray: ByteArray): Nel<Bilde> {
        val pdfDokument = Loader.loadPDF(pdfByteArray)
        val renderer = PDFRenderer(pdfDokument)
        val siderSomBilder = (0 until pdfDokument.numberOfPages).map {
            val bilde = renderer.renderImage(it)
            val baos = ByteArrayOutputStream()
            ImageIO.write(bilde, "png", baos)
            Bilde(Detect.IMAGE_PNG, baos.toByteArray())
        }
        pdfDokument.close()
        return requireNotNull(siderSomBilder.toNonEmptyListOrNull()) { "PDF-en hadde ingen sider" }
    }

    /** En sammenslåing uten PDF-er gir en tom, ødelagt fil; derfor [Nel]. */
    fun slåSammenPdfer(pdfbaListe: Nel<ByteArray>): ByteArray {
        val pdfMerger = PDFMergerUtility()
        val baosUt = ByteArrayOutputStream()
        pdfMerger.destinationStream = baosUt
        pdfbaListe.forEach {
            val inputStream = ByteArrayInputStream(it)
            pdfMerger.addSource(RandomAccessReadBuffer(inputStream))
        }
        pdfMerger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache()); // TODO: Sjekk ut memory settings
        return baosUt.toByteArray()
    }
}

/** [type] er media-typen bildet sendes med til pdfgen, f.eks. [Detect.IMAGE_PNG]. */
class Bilde(
    val type: String,
    val data: ByteArray,
)
