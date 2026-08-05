package no.nav.tiltakspenger.soknad.api.pdf

import arrow.core.Either
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.soknad.api.domain.Søknad
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

interface PdfGenerator {
    suspend fun genererPdf(søknad: Søknad): Either<HttpKlientError, ByteArray>

    suspend fun konverterVedlegg(vedlegg: List<Vedlegg>): Either<KunneIkkeKonvertereVedlegg, List<Vedlegg>>
}
