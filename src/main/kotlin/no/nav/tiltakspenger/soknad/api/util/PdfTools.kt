package no.nav.tiltakspenger.soknad.api.util

import io.ktor.http.ContentType
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
    fun konverterPdfTilBilder(pdfByteArray: ByteArray): List<Bilde> {
        val pdfDokument = Loader.loadPDF(pdfByteArray)
        val renderer = PDFRenderer(pdfDokument)
        val siderSomBilder = (0 until pdfDokument.numberOfPages).map {
            val bilde = renderer.renderImage(it)
            val baos = ByteArrayOutputStream()
            ImageIO.write(bilde, "png", baos)
            Bilde(ContentType.Image.PNG, baos.toByteArray())
        }
        pdfDokument.close()
        return siderSomBilder
    }

    fun slåSammenPdfer(pdfbaListe: List<ByteArray>): ByteArray {
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
class Bilde(
    val type: ContentType,
    val data: ByteArray,
)
