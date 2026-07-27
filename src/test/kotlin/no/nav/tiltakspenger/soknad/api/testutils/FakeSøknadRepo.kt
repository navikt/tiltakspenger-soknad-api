package no.nav.tiltakspenger.soknad.api.testutils

import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.soknad.api.soknad.Applikasjonseier
import no.nav.tiltakspenger.soknad.api.soknad.MottattSøknad
import no.nav.tiltakspenger.soknad.api.soknad.SøknadRepo
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [SøknadRepo] for e2e-tester som ikke tester noe databasespesifikt.
 * Filtreringen speiler where-klausulene i [no.nav.tiltakspenger.soknad.api.soknad.SøknadPostgresRepo], som har sin egen test mot ekte Postgres.
 */
class FakeSøknadRepo : SøknadRepo {
    private val søknader = ConcurrentHashMap<SøknadId, MottattSøknad>()

    /** Settes av tester som skal øve feilstien når lagring feiler. */
    var kastVedLagring: Throwable? = null

    val alle: List<MottattSøknad> get() = søknader.values.toList()

    override fun lagre(mottattSøknad: MottattSøknad) {
        kastVedLagring?.let { throw it }
        søknader[mottattSøknad.id] = mottattSøknad
    }

    override fun oppdater(mottattSøknad: MottattSøknad) {
        søknader[mottattSøknad.id] = mottattSøknad
    }

    override fun hentSøknaderUtenSaksnummer(): List<MottattSøknad> =
        alle.filter { it.saksnummer == null && it.eier == Applikasjonseier.Tiltakspenger }

    override fun hentSøknaderKlareForJournalføring(): List<MottattSøknad> =
        alle.filter { it.journalført == null && (it.saksnummer != null || it.eier == Applikasjonseier.Arena) }

    override fun hentSøknaderSomSkalSendesTilSaksbehandlingApi(): List<MottattSøknad> =
        alle.filter { it.journalført != null && it.sendtTilVedtak == null && it.eier == Applikasjonseier.Tiltakspenger }

    override fun hentBrukersSøknader(fnr: String, eier: Applikasjonseier): List<MottattSøknad> =
        alle.filter { it.fnr == fnr && it.eier == eier }

    override fun hentSøknad(søknadId: SøknadId): MottattSøknad? = søknader[søknadId]

    override fun oppdaterFnr(gammeltFnr: Fnr, nyttFnr: Fnr) {
        alle.filter { it.fnr == gammeltFnr.verdi }.forEach { søknad ->
            søknader[søknad.id] = søknad.copy(fnr = nyttFnr.verdi)
        }
    }
}
