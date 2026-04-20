package no.nav.tiltakspenger.soknad.api.dokarkiv

import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.soknad.api.domain.Søknad
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

class DokarkivService(
    private val dokarkivClient: DokarkivClient,
) {
    suspend fun sendPdfTilDokarkiv(
        pdf: ByteArray,
        søknad: Søknad,
        fnr: String,
        vedlegg: List<Vedlegg>,
        søknadId: SøknadId,
        callId: String,
        saksnummer: String?,
    ): JournalpostId {
        val journalpost = JournalpostRequest.from(
            fnr = fnr,
            søknad = søknad,
            pdf = pdf,
            vedlegg = vedlegg,
            saksnummer = saksnummer,
        )
        return dokarkivClient.opprettJournalpost(journalpost, søknadId, callId)
    }
}
