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
        "8d569657-7f12-498c-bc8a-91f95f8f7cfd",
        "c61a732e-975c-4351-b774-eea3eb7975a1",
        "7f848f25-e343-4c17-9317-2df6bc2a0015",
        "81bf7cee-41b6-4604-b840-fb54116eeea6",
        "6480717f-882a-4c35-87db-54004e846cf0",
        "17de7b99-5f85-4015-bb3b-9c4030cad23d",
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
