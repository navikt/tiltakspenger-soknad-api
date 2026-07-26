package no.nav.tiltakspenger.soknad.api.testutils

import arrow.atomic.Atomic
import arrow.atomic.update
import arrow.core.Either
import arrow.core.right
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.soknad.api.domain.Søknad
import no.nav.tiltakspenger.soknad.api.pdf.KunneIkkeKonvertereVedlegg
import no.nav.tiltakspenger.soknad.api.pdf.PdfGenerator
import no.nav.tiltakspenger.soknad.api.util.Detect.APPLICATON_PDF
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

/**
 * Fake for [PdfGenerator], som svarer med en gyldig, tom PDF.
 * Konverteringen beholder filnavnet og bytter innholdet med den samme PDF-en, slik at journalføringen har noe å sende videre.
 */
class PdfGeneratorFake : PdfGenerator {
    private val genererte = Atomic(emptyList<Søknad>())
    val antallGenererte: Int get() = genererte.get().size

    override suspend fun genererPdf(søknad: Søknad): Either<HttpKlientError, Pair<ByteArray, ByteArray?>> {
        genererte.update { it + søknad }
        return (enkelPdf() to null).right()
    }

    override suspend fun konverterVedlegg(vedlegg: List<Vedlegg>): Either<KunneIkkeKonvertereVedlegg, List<Vedlegg>> =
        vedlegg.map { Vedlegg(filnavn = it.filnavn, contentType = APPLICATON_PDF, dokument = enkelPdf()) }.right()
}
