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
        // https://trello.com/c/HVsVNWrF/1575-ta-inn-nye-brukere-i-v%C3%A5r-l%C3%B8sning:
        "e4e18ddb-defa-4cb9-85dd-d0184076d61b",
        "8859e28c-75be-4f95-a031-d4f05e3cbb72",
        "53ab50ab-96df-4303-a205-97ad0d9b2f12",
        "cb7d17d3-ccb7-4b2a-a28c-d8b09ee32eab",
        "ea984bc6-ed90-40ba-8f58-e3dd243498f6",
        // https://trello.com/c/4qIFTH5k/1599-legge-inn-nye-gjennomf%C3%B8ringer-i-prod-der-eventuelle-s%C3%B8kere-skal-sendes-til-oss:
        "8d569657-7f12-498c-bc8a-91f95f8f7cfd",
        "c61a732e-975c-4351-b774-eea3eb7975a1",
        "7f848f25-e343-4c17-9317-2df6bc2a0015",
        "81bf7cee-41b6-4604-b840-fb54116eeea6",
        "6480717f-882a-4c35-87db-54004e846cf0",
        "17de7b99-5f85-4015-bb3b-9c4030cad23d",
        // https://trello.com/c/9g3yDuCM/1624-legge-til-nye-tiltak-som-vi-skal-motta-s%C3%B8knader-for:
        "b55e4613-d1b2-4c2d-8fd9-42d6cbea4fbf",
        "b6164a8a-3ad3-4363-b947-0fc717d644cf",
        "7f80750a-45b2-449c-b03b-d1cf1393f762",
        "ef4428c5-1dd6-4c93-884f-c8dd5eef6e60",
        "2e1cd8f0-1c62-4ad7-869c-1c858f333b77",
        "0099cd72-06ba-4782-8dd4-b494d2da471d",
        "e02fc0f2-fab3-449a-9ecc-01628b9b7b56",
        "78b2a4a0-f3c0-47db-9a57-370dc897b157",
        // https://trello.com/c/jEVYSPwy/1648-s%C3%B8knader-for-4-nye-tiltak-rutes-til-tp-sak
        "4ce8a248-7c01-4e90-8bc3-ec4087078a87",
        "7291d4a5-7876-4e94-803f-115858e177b0",
        "fe803908-811f-41d1-90c9-228f3dbb7e91",
        "51bc251f-44ff-4467-bdc4-fce50e6b2e62",
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
