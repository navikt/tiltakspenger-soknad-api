package no.nav.tiltakspenger.soknad.api.soknad

import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.soknad.api.domain.Søknad
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import java.time.LocalDateTime

/**
 * Søknaden slik den er mottatt fra bruker. Her har vi kun gjort virussjekk av vedlegg.
 */
data class MottattSøknad(
    val id: SøknadId,
    val versjon: String,
    val søknad: Søknad?,
    val søknadSpm: SpørsmålsbesvarelserDTO,
    val vedlegg: List<Vedlegg>,
    val acr: String,
    val fnr: String,
    val fornavn: String?,
    val etternavn: String?,
    // TODO post-mvp jah: Skal vi endre navnet på denne? Til saksbehandlingApi eller noe mer generelt?
    val sendtTilVedtak: LocalDateTime?,
    val journalført: LocalDateTime?,
    val journalpostId: JournalpostId?,
    val opprettet: LocalDateTime,
    val eier: Applikasjonseier,
    val saksnummer: String?,
)
