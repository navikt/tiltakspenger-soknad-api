package no.nav.tiltakspenger.soknad.api.soknad

import arrow.core.Either
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.logging.sikkerlogg
import no.nav.tiltakspenger.soknad.api.Configuration

class NySøknadService(
    private val søknadRepo: SøknadRepo,
) {
    private val log = KotlinLogging.logger {}

    private val deltakerIderSomSkalTilTpsak = listOf(
        "0b9d57f0-0ece-44f3-a918-d0721ec526ca",
        "fda6f295-201e-4522-b94e-99d54b537f94",
    )

    // TODO post-mvp jah: Flytt domenelogikk fra route og inn hit.
    fun nySøknad(
        nySøknadCommand: NySøknadCommand,
    ): Either<KunneIkkeMottaNySøknad, Unit> {
        val eier = getEier(nySøknadCommand)
        val søknad: MottattSøknad = nySøknadCommand.toDomain(eier)
        return Either.catch {
            søknadRepo.lagre(søknad)
            log.info { "Søknad mottatt og lagret. SøknadId: ${søknad.id}. Acr: ${nySøknadCommand.acr}. Antall vedlegg: ${nySøknadCommand.vedlegg.size}. Innsendingstidspunkt: ${nySøknadCommand.innsendingTidspunkt}" }
        }.mapLeft {
            log.error(it) { "Feil under lagring av søknad. Se sikkerlogg for mer kontekst. Antall vedlegg: ${nySøknadCommand.vedlegg.size}. Innsendingstidspunkt: ${nySøknadCommand.innsendingTidspunkt}" }
            sikkerlogg.error(it) { "Feil under lagring av søknad. Antall vedlegg: ${nySøknadCommand.vedlegg.size}. Innsendingstidspunkt: ${nySøknadCommand.innsendingTidspunkt}. Fnr: ${nySøknadCommand.fnr}. " }
            KunneIkkeMottaNySøknad.KunneIkkeLagreSøknad
        }
    }

    // Søknader sendes by default til arena i prod, med mindre brukeren har søknader hos oss fra før, eller blir
    // manuelt lagt til i listen over deltakere som skal til oss.
    private fun getEier(nySøknadCommand: NySøknadCommand): Applikasjonseier {
        val brukerHarSøknaderSomEiesAvTiltakspenger =
            søknadRepo.hentBrukersSøknader(nySøknadCommand.fnr, Applikasjonseier.Tiltakspenger).isNotEmpty()
        val brukerErForhandsgodkjent = nySøknadCommand.brukersBesvarelser.tiltak.aktivitetId in deltakerIderSomSkalTilTpsak

        return if (Configuration.isProd() && !brukerHarSøknaderSomEiesAvTiltakspenger && !brukerErForhandsgodkjent) {
            Applikasjonseier.Arena
        } else {
            Applikasjonseier.Tiltakspenger
        }
    }
}

sealed interface KunneIkkeMottaNySøknad {
    data object KunneIkkeLagreSøknad : KunneIkkeMottaNySøknad
}
