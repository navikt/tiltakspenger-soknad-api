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

    private val gjennomforingerSomSkalTilTpsak = listOf<String>(
        "e4e18ddb-defa-4cb9-85dd-d0184076d61b",
        "8859e28c-75be-4f95-a031-d4f05e3cbb72",
        "53ab50ab-96df-4303-a205-97ad0d9b2f12",
        "cb7d17d3-ccb7-4b2a-a28c-d8b09ee32eab",
        "ea984bc6-ed90-40ba-8f58-e3dd243498f6",
    )

    // TODO post-mvp jah: Flytt domenelogikk fra route og inn hit.
    fun nySøknad(
        nySøknadCommand: NySøknadCommand,
        adressebeskyttelseGradering: AdressebeskyttelseGradering,
    ): Either<KunneIkkeMottaNySøknad, Unit> {
        val eier = getEier(nySøknadCommand, adressebeskyttelseGradering, gjennomforingerSomSkalTilTpsak)
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
    // kode 6 (og ikke har søknader hos oss fra før) eller blir manuelt lagt til i listen over gjennomføringer
    // der søknaden skal rutes til oss.
    fun getEier(
        nySøknadCommand: NySøknadCommand,
        adressebeskyttelseGradering: AdressebeskyttelseGradering,
        gjennomforingerSomSkalTilTpsak: List<String>,
    ): Applikasjonseier {
        val brukerHarSøknaderSomEiesAvTiltakspenger =
            søknadRepo.hentBrukersSøknader(nySøknadCommand.fnr, Applikasjonseier.Tiltakspenger).isNotEmpty()
        val forhandsgodkjentTiltak =
            nySøknadCommand.brukersBesvarelser.tiltak.gjennomforingId?.let { it in gjennomforingerSomSkalTilTpsak } == true
        val erKode6 = adressebeskyttelseGradering == AdressebeskyttelseGradering.STRENGT_FORTROLIG ||
            adressebeskyttelseGradering == AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND

        return if (Configuration.isProd()) {
            if (brukerHarSøknaderSomEiesAvTiltakspenger || (forhandsgodkjentTiltak && !erKode6)) {
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
