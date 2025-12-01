package no.nav.tiltakspenger.soknad.api.soknad

import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import java.time.LocalDateTime

data class NySøknadCommand(
    val brukersBesvarelser: SpørsmålsbesvarelserDTO,
    val acr: String,
    val fnr: String,
    val vedlegg: List<Vedlegg>,
    val innsendingTidspunkt: LocalDateTime,
) {
    fun toDomain(): MottattSøknad {
        return MottattSøknad(
            id = SøknadId.random(),
            versjon = "1",
            søknad = null,
            søknadSpm = brukersBesvarelser,
            vedlegg = vedlegg,
            acr = acr,
            fnr = fnr,
            fornavn = null,
            etternavn = null,
            sendtTilVedtak = null,
            journalført = null,
            journalpostId = null,
            opprettet = innsendingTidspunkt,
            eier = Applikasjonseier.Tiltakspenger,
            saksnummer = null,
        )
    }
}
