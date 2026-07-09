package no.nav.tiltakspenger.soknad.api.pdf

import no.nav.tiltakspenger.soknad.api.domain.Søknad
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

interface PdfGenerator {
    /*
        TODO - pdfgenrs: skift tilbake til ByteArray når det er verifisert at PDF fra pdfgenrs er ok.
            Andreelementet er skygge-PDF-en fra pdfgenrs, kun satt i local/dev.
     */
    suspend fun genererPdf(søknad: Søknad): Pair<ByteArray, ByteArray?>
    suspend fun konverterVedlegg(vedlegg: List<Vedlegg>): List<Vedlegg>
}
