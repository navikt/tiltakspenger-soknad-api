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

/**
 * Verktøyene bak konverteringen av PDF-vedlegg: hver side rastreres til et bilde, bildene sendes gjennom pdfgen og settes sammen igjen.
 * Rastreringen er tilsiktet — den flater ut redigerbare skjemafelter før arkivering — men den koster tekstlaget og låser oppløsningen til 72 dpi.
 * Om det er riktig avveining er ikke avgjort; alternativene er beskrevet i navikt/tiltakspenger-soknad-api#865, og dagens oppførsel er låst i `PdfToolsTest`.
 */
object PdfTools {
    /**
     * Antall piksler hver side vil bli rendret til, uten å rendre noe.
     *
     * Målt på `cropBox` og ikke `mediaBox`, fordi det er cropBox [PDFRenderer] dimensjonerer bildet etter.
     * Brukes til å avvise sider som er for store til å rendres trygt, før de når [konverterPdfTilBilder].
     * Kaster hvis pdfbox ikke klarer å lese fila; kallstedet tar det som en valideringsfeil.
     */
    fun pikslerPerSide(pdfByteArray: ByteArray): List<Long> =
        Loader.loadPDF(pdfByteArray).use { dokument ->
            dokument.pages.map { side ->
                val boks = side.cropBox
                boks.width.toLong() * boks.height.toLong()
            }
        }

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
        // Alt holdes i minnet under sammenslåingen.
        // Det tåler dagens vedleggsgrenser, men skalerer med sidetallet, som ikke er begrenset — se #865 og navikt/tiltakspenger#46.
        // En temp-fil-cache er alternativet hvis rastreringen består; velges utflating i stedet, faller hele sammenslåingen bort.
        pdfMerger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache())
        return baosUt.toByteArray()
    }
}

/** [type] er media-typen bildet sendes med til pdfgen, f.eks. [Detect.IMAGE_PNG]. */
class Bilde(
    val type: String,
    val data: ByteArray,
)
