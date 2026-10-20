package no.nav.tiltakspenger.soknad.api.soknad

import arrow.core.Either
import arrow.core.flatMap
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.logging.Sikkerlogg

class NySøknadService(
    private val søknadRepo: SøknadRepo,
) {
    private val log = KotlinLogging.logger {}

    // TODO post-mvp jah: Flytt domenelogikk fra route og inn hit.
    fun nySøknad(
        nySøknadCommand: NySøknadCommand,
    ): Either<KunneIkkeMottaNySøknad, Unit> {
        val søknad: MottattSøknad = nySøknadCommand.toDomain()
        return Either.catch {
            søknadRepo.lagreHvisIkkeDuplikat(søknad)
        }.mapLeft {
            log.error(it) { "Feil under lagring av søknad. Se sikkerlogg for mer kontekst. Antall vedlegg: ${nySøknadCommand.vedlegg.size}. Innsendingstidspunkt: ${nySøknadCommand.innsendingTidspunkt}" }
            Sikkerlogg.error(it) { "Feil under lagring av søknad. Antall vedlegg: ${nySøknadCommand.vedlegg.size}. Innsendingstidspunkt: ${nySøknadCommand.innsendingTidspunkt}. Fnr: ${nySøknadCommand.fnr}. " }
            KunneIkkeMottaNySøknad.KunneIkkeLagreSøknad
        }.flatMap { lagret ->
            lagret.mapLeft { tidligereSøknad ->
                log.info { "Avviste søknad: samme tiltaksdeltakelse, periode og svar som en søknad vi allerede har mottatt. Tidligere søknadId: ${tidligereSøknad.id}. Mottatt: ${tidligereSøknad.mottatt}." }
                KunneIkkeMottaNySøknad.IngenEndringer(tidligereSøknad)
            }.onRight {
                log.info { "Søknad mottatt og lagret. SøknadId: ${søknad.id}. Acr: ${nySøknadCommand.acr}. Antall vedlegg: ${nySøknadCommand.vedlegg.size}. Innsendingstidspunkt: ${nySøknadCommand.innsendingTidspunkt}" }
            }
        }
    }
}

sealed interface KunneIkkeMottaNySøknad {
    data object KunneIkkeLagreSøknad : KunneIkkeMottaNySøknad

    /** Brukeren har allerede sendt inn en søknad for samme tiltaksdeltakelse og periode, med de samme svarene. */
    data class IngenEndringer(val tidligereSøknad: TidligereSøknad) : KunneIkkeMottaNySøknad
}
