package no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforing

import mu.KotlinLogging
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivService
import no.nav.tiltakspenger.soknad.api.domain.Søknad
import no.nav.tiltakspenger.soknad.api.pdf.PdfService
import no.nav.tiltakspenger.soknad.api.soknad.SpørsmålsbesvarelserDTO
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import java.time.LocalDateTime

class JournalforingService(
    private val pdfService: PdfService,
    private val dokarkivService: DokarkivService,
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
    ): Pair<String, Søknad> {
        val vedleggsnavn = vedlegg.map { it.filnavn }
        val søknad = Søknad.toSøknad(
            id = søknadId.toString(),
            spørsmålsbesvarelser = spørsmålsbesvarelser,
            fnr = fnr,
            fornavn = fornavn,
            etternavn = etternavn,
            acr = acr,
            innsendingTidspunkt = innsendingTidspunkt,
            vedleggsnavn = vedleggsnavn,
        )
        val pdf = pdfService.lagPdf(søknad)
        log.info { "Generering av søknadsPDF OK" }
        val vedleggSomPdfer = pdfService.konverterVedlegg(vedlegg)
        log.info { "Vedleggskonvertering OK" }
        val journalpostId = dokarkivService.sendPdfTilDokarkiv(
            pdf = pdf,
            søknad = søknad,
            fnr = fnr,
            vedlegg = vedleggSomPdfer,
            søknadId = søknadId,
            callId = callId,
            saksnummer = saksnummer,
        )
        return Pair(journalpostId, søknad)
    }
}
