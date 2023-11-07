package no.nav.tiltakspenger.soknad.api.dokument

import no.nav.tiltakspenger.soknad.api.domain.SøknadDTO
import no.nav.tiltakspenger.soknad.api.soknad.SøknadResponse
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

class DokumentServiceImpl(
    private val dokumentClient: DokumentClient,
) : DokumentService {
    override suspend fun sendSøknadTilDokument(
        søknadDTO: SøknadDTO,
        vedlegg: List<Vedlegg>,
    ): SøknadResponse {
        return dokumentClient.sendSøknadTilDokument(søknadDTO, vedlegg)
    }
}
