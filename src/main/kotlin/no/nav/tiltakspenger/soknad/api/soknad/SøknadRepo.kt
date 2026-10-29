package no.nav.tiltakspenger.soknad.api.soknad

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.libs.common.personopplysning.Fnr

/**
 * Lagring av mottatte søknader.
 * Implementasjonen mot Postgres er [SøknadPostgresRepo]; tester bruker en in-memory fake.
 */
interface SøknadRepo {
    fun lagre(mottattSøknad: MottattSøknad)

    /**
     * Lagrer søknaden, med mindre den er en duplikat av en tidligere søknad (se [duplikatAv]).
     * Oppslaget og lagringen skjer i én transaksjon under en lås per fnr, så to samtidige innsendinger ikke begge slipper gjennom.
     *
     * @return den tidligere søknaden som `Left` når søknaden ikke ble lagret.
     */
    fun lagreHvisIkkeDuplikat(mottattSøknad: MottattSøknad): Either<TidligereSøknad, Unit>

    /** Alle søknadene brukeren har sendt inn, uansett eier, med svarene men uten vedlegg. */
    fun hentTidligereSøknader(fnr: String): List<TidligereSøknad>

    fun oppdater(mottattSøknad: MottattSøknad)

    /**
     * Lagrer utfallet av behandlingen av søknaden.
     * Et utfall fra et eldre vedtak enn det som allerede er lagret, overskriver ikke.
     */
    fun lagreBehandlingsutfall(søknadId: SøknadId, behandlingsutfall: Behandlingsutfall): Either<SøknadFinnesIkke, Unit>

    /** Tiltakspenger-søknader som ennå ikke har fått tildelt saksnummer fra saksbehandling-api. */
    fun hentSøknaderUtenSaksnummer(): List<MottattSøknad>

    /** Tiltakspenger-søknader med saksnummer, og alle arena-søknader. */
    fun hentSøknaderKlareForJournalføring(): List<MottattSøknad>

    /** Journalførte tiltakspenger-søknader som ennå ikke er sendt videre til saksbehandling-api. */
    fun hentSøknaderSomSkalSendesTilSaksbehandlingApi(): List<MottattSøknad>

    fun hentBrukersSøknader(fnr: String, eier: Applikasjonseier): List<MottattSøknad>

    fun hentSøknad(søknadId: SøknadId): MottattSøknad?

    fun oppdaterFnr(gammeltFnr: Fnr, nyttFnr: Fnr)
}
