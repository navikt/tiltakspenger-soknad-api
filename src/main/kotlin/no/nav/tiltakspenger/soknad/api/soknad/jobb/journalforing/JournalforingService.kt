package no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforing

import arrow.core.Either
import arrow.core.raise.either
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.libs.httpklient.loggFeil
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivService
import no.nav.tiltakspenger.soknad.api.dokarkiv.KunneIkkeJournalføre
import no.nav.tiltakspenger.soknad.api.domain.Søknad
import no.nav.tiltakspenger.soknad.api.pdf.KunneIkkeKonvertereVedlegg
import no.nav.tiltakspenger.soknad.api.pdf.PdfService
import no.nav.tiltakspenger.soknad.api.soknad.SpørsmålsbesvarelserDTO
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import java.time.LocalDateTime

/**
 * Fellestypen for alt som kan gå galt på veien fra søknad til journalpost.
 * Alle variantene er allerede logget der de oppsto; jobben bruker dem kun til å hoppe over søknaden og forsøke på nytt ved neste kjøring.
 */
sealed interface KunneIkkeOpprettDokumenter {
    data object PdfGenereringFeilet : KunneIkkeOpprettDokumenter

    data object VedleggskonverteringFeilet : KunneIkkeOpprettDokumenter

    data object JournalføringFeilet : KunneIkkeOpprettDokumenter
}

class JournalforingService(
    private val pdfService: PdfService,
    private val dokarkivService: DokarkivService,
    private val sikkerlogg: Sikkerlogg,
) {
    private val log = KotlinLogging.logger {}

    suspend fun opprettDokumenterOgArkiverIDokarkiv(
        spørsmålsbesvarelser: SpørsmålsbesvarelserDTO,
        fnr: String,
        fornavn: String,
        etternavn: String,
        vedlegg: List<Vedlegg>,
        acr: String,
        innsendingTidspunkt: LocalDateTime,
        søknadId: SøknadId,
        saksnummer: String?,
        callId: String,
    ): Either<KunneIkkeOpprettDokumenter, Pair<JournalpostId, Søknad>> = either {
        val kontekst = "søknadId $søknadId, callId $callId"
        val søknad = Søknad.toSøknad(
            id = søknadId.toString(),
            spørsmålsbesvarelser = spørsmålsbesvarelser,
            fnr = fnr,
            fornavn = fornavn,
            etternavn = etternavn,
            acr = acr,
            innsendingTidspunkt = innsendingTidspunkt,
            vedleggsnavn = vedlegg.map { it.filnavn },
        )

        val pdf = pdfService.lagPdf(søknad).mapLeft { feil ->
            feil.loggFeil(log, "generering av søknadspdf", kontekst, sikkerlogg)
            KunneIkkeOpprettDokumenter.PdfGenereringFeilet
        }.bind()
        log.info { "Generering av søknadsPDF OK" }

        val vedleggSomPdfer = pdfService.konverterVedlegg(vedlegg).mapLeft { feil ->
            feil.logg(kontekst)
            KunneIkkeOpprettDokumenter.VedleggskonverteringFeilet
        }.bind()
        log.info { "Vedleggskonvertering OK" }

        val journalpostId = dokarkivService.sendPdfTilDokarkiv(
            pdf = pdf,
            søknad = søknad,
            fnr = fnr,
            vedlegg = vedleggSomPdfer,
            callId = callId,
            saksnummer = saksnummer,
        ).tilDomenefeil(kontekst).bind()

        journalpostId to søknad
    }

    private fun KunneIkkeKonvertereVedlegg.logg(kontekst: String) {
        when (this) {
            is KunneIkkeKonvertereVedlegg.KallFeilet -> feil.loggFeil(log, "konvertering av vedlegg", kontekst, sikkerlogg)

            is KunneIkkeKonvertereVedlegg.UgyldigFilformat ->
                log.error { "Kan ikke konvertere vedlegg med filformat $contentType. $kontekst." }
        }
    }

    private fun Either<KunneIkkeJournalføre, JournalpostId>.tilDomenefeil(
        kontekst: String,
    ): Either<KunneIkkeOpprettDokumenter, JournalpostId> = mapLeft { feil ->
        when (feil) {
            is KunneIkkeJournalføre.KallFeilet -> feil.feil.loggFeil(log, "journalføring i dokarkiv", kontekst, sikkerlogg)

            is KunneIkkeJournalføre.IkkeFerdigstilt ->
                log.error { "Journalpost ${feil.journalpostId} ble opprettet, men ikke ferdigstilt. $kontekst. ${sikkerlogg.seSikkerlogg}" }
        }
        KunneIkkeOpprettDokumenter.JournalføringFeilet
    }
}
