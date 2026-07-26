package no.nav.tiltakspenger.soknad.api.soknad.jobb

import arrow.core.getOrElse
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.httpklient.loggFeil
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.saksbehandlingApi.SaksbehandlingApiKlient
import no.nav.tiltakspenger.soknad.api.saksbehandlingApi.søknadMapper
import no.nav.tiltakspenger.soknad.api.soknad.Applikasjonseier
import no.nav.tiltakspenger.soknad.api.soknad.SøknadRepo
import no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforing.JournalforingService
import java.time.Clock

class SøknadJobbService(
    private val søknadRepo: SøknadRepo,
    private val pdlService: PdlService,
    private val journalforingService: JournalforingService,
    private val saksbehandlingApiKlient: SaksbehandlingApiKlient,
    private val clock: Clock,
    private val sikkerlogg: Sikkerlogg,
) {
    private val log = KotlinLogging.logger {}

    suspend fun hentEllerOpprettSaksnummer(correlationId: CorrelationId) {
        søknadRepo.hentSoknaderUtenSaksnummer().forEach { soknad ->
            log.info { "Henter eller oppretter saksnummer for søknad med id ${soknad.id}" }
            val saksnummer = saksbehandlingApiKlient.hentEllerOpprettSaksnummer(
                Fnr.fromString(soknad.fnr),
                correlationId,
            ).getOrElse { feil ->
                feil.loggFeil(log, "henting av saksnummer", "Hent saksnummer-jobb: søknadId ${soknad.id}", sikkerlogg)
                return@forEach
            }
            søknadRepo.oppdater(soknad.copy(saksnummer = saksnummer))
            log.info { "Hent saksnummer-jobb: Lagret saksnummer for søknad ${soknad.id} " }
        }
    }

    suspend fun journalførLagredeSøknader(correlationId: CorrelationId) {
        søknadRepo.hentAlleSøknadDbDtoSomIkkeErJournalført().forEach { søknad ->
            log.info { "Journalfør søknad jobb: Prøver å journalføre søknad med søknadId ${søknad.id}" }
            if (søknad.eier == Applikasjonseier.Tiltakspenger && søknad.saksnummer.isNullOrEmpty()) {
                log.error { "Søknad med id ${søknad.id} mangler saksnummer, kan ikke journalføre" }
                throw IllegalStateException("Kan ikke journalføre søknad som mangler saksnummer")
            }

            // Feilene er allerede logget i PdlService og JournalforingService; jobben hopper bare over søknaden og forsøker igjen neste kjøring.
            val navn = pdlService.hentNavnForFnr(Fnr.fromString(søknad.fnr), correlationId).getOrElse { return@forEach }
            val (journalpostId, søknadDto) = journalforingService.opprettDokumenterOgArkiverIDokarkiv(
                spørsmålsbesvarelser = søknad.søknadSpm,
                fnr = søknad.fnr,
                fornavn = navn.fornavn,
                etternavn = navn.etternavn,
                vedlegg = søknad.vedlegg,
                acr = søknad.acr,
                innsendingTidspunkt = søknad.opprettet,
                søknadId = søknad.id,
                saksnummer = søknad.saksnummer,
                callId = correlationId.toString(),
            ).getOrElse { return@forEach }

            søknadRepo.oppdater(
                søknad.copy(
                    søknad = søknadDto,
                    fornavn = navn.fornavn,
                    etternavn = navn.etternavn,
                    journalpostId = journalpostId,
                    journalført = nå(clock),
                ),
            )
            log.info { "Journalfør søknad jobb: Vi har journalført søknad ${søknad.id} " }
        }
    }

    suspend fun sendJournalførteSøknaderTilSaksbehandlingApi(correlationId: CorrelationId) {
        søknadRepo.hentSøknaderSomSkalSendesTilSaksbehandlingApi().forEach { søknad ->
            checkNotNull(søknad.søknad) { "Send søknad til saksbehandling-api jobb: Søknad ${søknad.id} mangler søknad" }
            checkNotNull(søknad.journalpostId) { "Send søknad til saksbehandling-api jobb: Søknad ${søknad.id} mangler journalpostId" }
            checkNotNull(søknad.saksnummer) { "Send søknad til saksbehandling-api jobb: Søknad ${søknad.id} mangler saksnummer" }
            val sendtTilSaksbehandlingApi = nå(clock)
            saksbehandlingApiKlient.sendSøknad(
                søknadDTO = søknadMapper(
                    søknad = søknad.søknad,
                    jounalpostId = søknad.journalpostId,
                    saksnummer = søknad.saksnummer,
                ),
                correlationId = correlationId,
            ).getOrElse { feil ->
                feil.loggFeil(
                    log,
                    "sending av søknad til saksbehandling-api",
                    "Send søknad til saksbehandling-api jobb: søknadId ${søknad.id}. Denne vil prøves på nytt.",
                    sikkerlogg,
                )
                return@forEach
            }
            log.info { "Send søknad til saksbehandling-api jobb: Søknad ${søknad.id} er sendt til saksbehandling-api - prøver å lagre utsendingstidspunktet" }
            søknadRepo.oppdater(søknad.copy(sendtTilVedtak = sendtTilSaksbehandlingApi))
            log.info { "Send søknad til saksbehandling-api jobb: Oppdatert utsendingstidspunktet til $sendtTilSaksbehandlingApi for søknad ${søknad.id}" }
        }
    }
}
