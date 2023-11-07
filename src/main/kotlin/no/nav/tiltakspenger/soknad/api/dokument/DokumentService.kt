package no.nav.tiltakspenger.soknad.api.dokument

import no.nav.tiltakspenger.soknad.api.domain.SøknadDTO
import no.nav.tiltakspenger.soknad.api.soknad.SøknadResponse
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
interface DokumentService {
    suspend fun sendSøknadTilDokument(
        søknadDTO: SøknadDTO,
        vedlegg: List<Vedlegg>,
    ): SøknadResponse
}
