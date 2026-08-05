package no.nav.tiltakspenger.soknad.api.pdf

import arrow.core.Either
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.soknad.api.domain.Søknad
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

class PdfServiceImpl(
    private val pdfGenerator: PdfGenerator,
) : PdfService {
    override suspend fun lagPdf(søknad: Søknad): Either<HttpKlientError, ByteArray> =
        pdfGenerator.genererPdf(søknad)
    override suspend fun konverterVedlegg(vedlegg: List<Vedlegg>) =
        pdfGenerator.konverterVedlegg(vedlegg)
}
