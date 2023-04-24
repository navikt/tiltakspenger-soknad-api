package no.nav.tiltakspenger.soknad.api.util

import io.ktor.http.ContentType
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File

// WIP

class PdfToImageConverter {
    suspend fun convertPdfToImage(pdfFile: File) {
        val pdfDokument = PDDocument.load(pdfFile)
        val renderer = PDFRenderer(pdfDokument)
        val siderSomBilder = (0 until pdfDokument.numberOfPages).map { renderer.renderImage(it) }
    }
}
class Bilde(
    val type: ContentType,
    val data: ByteArray,
)