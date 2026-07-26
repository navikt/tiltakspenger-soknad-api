package no.nav.tiltakspenger.soknad.api.pdf

import arrow.core.Either
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.soknad.api.domain.Søknad
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

interface PdfGenerator {
    /*
        TODO - pdfgenrs: skift tilbake til ByteArray når det er verifisert at PDF fra pdfgenrs er ok.
            Andreelementet er skygge-PDF-en fra pdfgenrs, kun satt i local/dev.
     */
    suspend fun genererPdf(søknad: Søknad): Either<HttpKlientError, Pair<ByteArray, ByteArray?>>

    suspend fun konverterVedlegg(vedlegg: List<Vedlegg>): Either<KunneIkkeKonvertereVedlegg, List<Vedlegg>>
}
