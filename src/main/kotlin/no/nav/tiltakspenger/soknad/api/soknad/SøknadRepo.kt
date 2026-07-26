package no.nav.tiltakspenger.soknad.api.soknad

import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.SøknadId

/**
 * Lagring av mottatte søknader.
 * Implementasjonen mot Postgres er [SøknadPostgresRepo]; tester bruker en in-memory fake.
 */
interface SøknadRepo {
    fun lagre(dto: MottattSøknad)

    fun oppdater(dto: MottattSøknad)

    /** Tiltakspenger-søknader som ennå ikke har fått tildelt saksnummer fra saksbehandling-api. */
    fun hentSoknaderUtenSaksnummer(): List<MottattSøknad>

    /** Søknader som er klare for journalføring, altså tiltakspenger-søknader med saksnummer og alle arena-søknader. */
    fun hentAlleSøknadDbDtoSomIkkeErJournalført(): List<MottattSøknad>

    /** Journalførte tiltakspenger-søknader som ennå ikke er sendt videre til saksbehandling-api. */
    fun hentSøknaderSomSkalSendesTilSaksbehandlingApi(): List<MottattSøknad>

    fun hentBrukersSøknader(fnr: String, eier: Applikasjonseier): List<MottattSøknad>

    fun hentSoknad(soknadId: SøknadId): MottattSøknad?

    fun oppdaterFnr(gammeltFnr: Fnr, nyttFnr: Fnr)
}
