package no.nav.tiltakspenger.soknad.api.pdf

import no.nav.tiltakspenger.soknad.api.domain.Søknad
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

interface PdfService {
    suspend fun lagPdf(søknad: Søknad): Pair<ByteArray, ByteArray?>
    suspend fun konverterVedlegg(vedlegg: List<Vedlegg>): List<Vedlegg>
}
