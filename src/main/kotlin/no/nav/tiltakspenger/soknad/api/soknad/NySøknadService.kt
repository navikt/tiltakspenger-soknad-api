package no.nav.tiltakspenger.soknad.api.soknad

import arrow.core.Either
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.soknad.api.Configuration
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering

class NySøknadService(
    private val søknadRepo: SøknadRepo,
) {
    private val log = KotlinLogging.logger {}

    private val deltakerIderSomSkalTilTpsak = listOf(
        "fda6f295-201e-4522-b94e-99d54b537f94",
    )

    // TODO post-mvp jah: Flytt domenelogikk fra route og inn hit.
    fun nySøknad(
        nySøknadCommand: NySøknadCommand,
        adressebeskyttelseGradering: AdressebeskyttelseGradering,
    ): Either<KunneIkkeMottaNySøknad, Unit> {
        val eier = getEier(nySøknadCommand, adressebeskyttelseGradering)
        val søknad: MottattSøknad = nySøknadCommand.toDomain(eier)
        return Either.catch {
            søknadRepo.lagre(søknad)
            log.info { "Søknad mottatt og lagret. SøknadId: ${søknad.id}. Acr: ${nySøknadCommand.acr}. Antall vedlegg: ${nySøknadCommand.vedlegg.size}. Innsendingstidspunkt: ${nySøknadCommand.innsendingTidspunkt}" }
        }.mapLeft {
            log.error(it) { "Feil under lagring av søknad. Se sikkerlogg for mer kontekst. Antall vedlegg: ${nySøknadCommand.vedlegg.size}. Innsendingstidspunkt: ${nySøknadCommand.innsendingTidspunkt}" }
            Sikkerlogg.error(it) { "Feil under lagring av søknad. Antall vedlegg: ${nySøknadCommand.vedlegg.size}. Innsendingstidspunkt: ${nySøknadCommand.innsendingTidspunkt}. Fnr: ${nySøknadCommand.fnr}. " }
            KunneIkkeMottaNySøknad.KunneIkkeLagreSøknad
        }
    }

    // Søknader sendes by default til arena i prod, med mindre brukeren har søknader hos oss fra før, er
    // kode 6 (og ikke har søknader hos oss fra før) eller blir manuelt lagt til i listen over deltakere
    // som skal til oss.
    private fun getEier(
        nySøknadCommand: NySøknadCommand,
        adressebeskyttelseGradering: AdressebeskyttelseGradering,
    ): Applikasjonseier {
        val brukerHarSøknaderSomEiesAvTiltakspenger =
            søknadRepo.hentBrukersSøknader(nySøknadCommand.fnr, Applikasjonseier.Tiltakspenger).isNotEmpty()
        val brukerErForhandsgodkjent =
            nySøknadCommand.brukersBesvarelser.tiltak.aktivitetId in deltakerIderSomSkalTilTpsak
        val erKode6 = adressebeskyttelseGradering == AdressebeskyttelseGradering.STRENGT_FORTROLIG ||
            adressebeskyttelseGradering == AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND

        return if (Configuration.isProd()) {
            if (brukerHarSøknaderSomEiesAvTiltakspenger || (brukerErForhandsgodkjent && !erKode6)) {
                Applikasjonseier.Tiltakspenger
            } else {
                Applikasjonseier.Arena
            }
        } else {
            Applikasjonseier.Tiltakspenger
        }
    }
}

sealed interface KunneIkkeMottaNySøknad {
    data object KunneIkkeLagreSøknad : KunneIkkeMottaNySøknad
}
