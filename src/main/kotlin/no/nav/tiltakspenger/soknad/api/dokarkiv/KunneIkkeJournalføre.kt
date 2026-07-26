package no.nav.tiltakspenger.soknad.api.dokarkiv

import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError

/**
 * Feil ved opprettelse av journalpost i dokarkiv.
 * Begge variantene er fatale for journalføringen av den enkelte søknaden, som før migreringen, og jobben forsøker på nytt ved neste kjøring.
 */
sealed interface KunneIkkeJournalføre {
    /** Selve kallet feilet — eller dedupliserings-`409`-en kom med en body vi ikke kunne lese. */
    data class KallFeilet(val feil: HttpKlientError) : KunneIkkeJournalføre

    /**
     * Journalposten ble opprettet, men ikke ferdigstilt, selv om vi ba om automatisk ferdigstilling.
     * [journalpostId] tas med fordi den trengs for å finne igjen den halvferdige journalposten manuelt.
     */
    data class IkkeFerdigstilt(val journalpostId: JournalpostId) : KunneIkkeJournalføre
}
