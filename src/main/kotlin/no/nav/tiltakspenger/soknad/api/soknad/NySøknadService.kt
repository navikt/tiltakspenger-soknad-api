package no.nav.tiltakspenger.soknad.api.soknad

import arrow.core.Either
import mu.KotlinLogging
import no.nav.tiltakspenger.libs.logging.sikkerlogg
import no.nav.tiltakspenger.soknad.api.Configuration

class NySøknadService(
    private val søknadRepo: SøknadRepo,
) {
    private val log = KotlinLogging.logger {}

    // TODO post-mvp jah: Flytt domenelogikk fra route og inn hit.
    fun nySøknad(
        nySøknadCommand: NySøknadCommand,
    ): Either<KunneIkkeMottaNySøknad, Unit> {
        val eier: Applikasjonseier = if (Configuration.isProd()) {
            // kommentar jah: Vi defaulter til Arena fram til vi tar over ruting. For MVP-brukerne våre endrer vi dette flagget direkte i databasen.
            Applikasjonseier.Arena
        } else {
            Applikasjonseier.Tiltakspenger
        }
        val søknad: MottattSøknad = nySøknadCommand.toDomain(eier)
        return Either.catch {
            søknadRepo.lagre(søknad)
            log.info { "Søknad mottatt og lagret. SøknadId: ${søknad.id}. Acr: ${nySøknadCommand.acr}. Antall vedlegg: ${nySøknadCommand.vedlegg.size}. Innsendingstidspunkt: ${nySøknadCommand.innsendingTidspunkt}" }
        }.mapLeft {
            log.error(RuntimeException("Trigger stacktrace for enklere debug.")) { "Feil under lagring av søknad. Se sikkerlogg for mer kontekst. Antall vedlegg: ${nySøknadCommand.vedlegg.size}. Innsendingstidspunkt: ${nySøknadCommand.innsendingTidspunkt}" }
            sikkerlogg.error(it) { "Feil under lagring av søknad. Antall vedlegg: ${nySøknadCommand.vedlegg.size}. Innsendingstidspunkt: ${nySøknadCommand.innsendingTidspunkt}. Fnr: ${nySøknadCommand.fnr}. " }
            KunneIkkeMottaNySøknad.KunneIkkeLagreSøknad
        }
    }
}

sealed interface KunneIkkeMottaNySøknad {
    data object KunneIkkeLagreSøknad : KunneIkkeMottaNySøknad
}
