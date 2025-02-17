package no.nav.tiltakspenger.soknad.api.soknad.jobb

import mu.KotlinLogging
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.logging.sikkerlogg
import no.nav.tiltakspenger.soknad.api.saksbehandlingApi.SaksbehandlingApiKlient
import no.nav.tiltakspenger.soknad.api.saksbehandlingApi.søknadMapper
import no.nav.tiltakspenger.soknad.api.soknad.Applikasjonseier
import no.nav.tiltakspenger.soknad.api.soknad.SøknadRepo
import no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforing.JournalforingService
import no.nav.tiltakspenger.soknad.api.soknad.jobb.person.PersonHttpklient
import java.time.LocalDateTime

class SøknadJobbService(
    private val søknadRepo: SøknadRepo,
    private val personHttpklient: PersonHttpklient,
    private val journalforingService: JournalforingService,
    private val saksbehandlingApiKlient: SaksbehandlingApiKlient,
) {
    private val log = KotlinLogging.logger {}
    suspend fun hentEllerOpprettSaksnummer(correlationId: CorrelationId) {
        søknadRepo.hentSoknaderUtenSaksnummer().forEach { soknad ->
            log.info { "Henter eller oppretter saksnummer for søknad med id ${soknad.id}" }
            val saksnummer = try {
                saksbehandlingApiKlient.hentEllerOpprettSaksnummer(
                    Fnr.fromString(soknad.fnr),
                    correlationId,
                )
            } catch (e: Exception) {
                log.error(e) { "Hent saksnummer-jobb: Feil ved henting av saksnummer for søknadId ${soknad.id}, ${e.message}" }
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

            val navn = try {
                personHttpklient.hentNavnForFnr(Fnr.fromString(søknad.fnr))
            } catch (e: Exception) {
                log.error(RuntimeException("Trigger stacktrace for enklere debug.")) { "Journalfør søknad jobb: Feil ved henting av navn fra PDL for søknadId ${søknad.id}" }
                sikkerlogg.error(e) { "Journalfør søknad jobb: Feil ved henting av navn fra PDL for søknadId ${søknad.id}" }
                return@forEach
            }
            val (journalpostId, søknadDto) = try {
                journalforingService.opprettDokumenterOgArkiverIDokarkiv(
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
                )
            } catch (e: Exception) {
                log.error(RuntimeException("Trigger stacktrace for enklere debug.")) { "Journalfør søknad jobb: Feil under journalføring mot Dokarkiv for søknadId ${søknad.id}" }
                sikkerlogg.error(e) { "Journalfør søknad jobb: Feil under journalføring mot Dokarkiv for søknadId ${søknad.id}" }
                return@forEach
            }
            søknadRepo.oppdater(
                søknad.copy(
                    søknad = søknadDto,
                    fornavn = navn.fornavn,
                    etternavn = navn.etternavn,
                    journalpostId = journalpostId,
                    journalført = LocalDateTime.now(),
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
            try {
                val sendtTilSaksbehandlingApi = LocalDateTime.now()
                saksbehandlingApiKlient.sendSøknad(
                    søknadDTO = søknadMapper(
                        søknad = søknad.søknad,
                        jounalpostId = søknad.journalpostId,
                        saksnummer = søknad.saksnummer,
                    ),
                    correlationId = correlationId,
                )
                log.info { "Send søknad til saksbehandling-api jobb: Søknad ${søknad.id} er sendt til saksbehandling-api - prøver lagre utsendingstidspuktet" }
                søknadRepo.oppdater(søknad.copy(sendtTilVedtak = sendtTilSaksbehandlingApi))
                log.info { "Send søknad til saksbehandling-api jobb: Oppdatert utsendingstidspunktet til $sendtTilSaksbehandlingApi for søknad ${søknad.id}" }
            } catch (e: Exception) {
                log.error(RuntimeException("Trigger stacktrace for enklere debug.")) { "Send søknad til saksbehandling-api jobb: Feil ved sending av søknad ${søknad.id} til saksbehandling-api. Denne vil prøves på nytt. Se sikkerlogg for mer info." }
                sikkerlogg.error(e) { "Send søknad til saksbehandling-api jobb:  Feil ved sending av søknad ${søknad.id} til saksbehandling-api. Denne vil prøves på nytt." }
            }
        }
    }
}
