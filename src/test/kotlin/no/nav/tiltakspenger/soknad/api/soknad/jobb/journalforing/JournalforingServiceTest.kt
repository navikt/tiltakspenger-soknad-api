package no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforing

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata
import no.nav.tiltakspenger.libs.httpklient.HttpKlientTidsstempler
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivService
import no.nav.tiltakspenger.soknad.api.dokarkiv.KunneIkkeJournalføre
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.pdf.KunneIkkeKonvertereVedlegg
import no.nav.tiltakspenger.soknad.api.pdf.PdfService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.ZERO

// TODO jah: Hele journalførings-flyten mockes her (PdfService + DokarkivService).
//  Bytt i størst mulig grad til e2e-test og fake kun klientene
class JournalforingServiceTest {
    private val pdfService = mockk<PdfService>()
    private val dokarkivService = mockk<DokarkivService>()
    private val journalforingService = JournalforingService(pdfService, dokarkivService, Sikkerlogg)
    private val journalpostId = JournalpostId("123")

    @BeforeEach
    fun clearMockData() {
        clearMocks(pdfService, dokarkivService)
    }

    private suspend fun opprettDokumenterOgArkiver() = journalforingService.opprettDokumenterOgArkiverIDokarkiv(
        spørsmålsbesvarelser = mockSpørsmålsbesvarelser(),
        fnr = "12345678910",
        fornavn = "Fornavn",
        etternavn = "Etternavn",
        vedlegg = emptyList(),
        acr = "acr",
        innsendingTidspunkt = nå(fixedClock),
        søknadId = SøknadId.random(),
        saksnummer = "saksnummer",
        callId = "callId",
    )

    @Test
    fun `journalfører søknaden og returnerer journalpostId`() = runTest {
        coEvery { pdfService.lagPdf(any()) } returns Pair(ByteArray(1), null).right()
        coEvery { pdfService.konverterVedlegg(any()) } returns emptyList<no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg>().right()
        coEvery { dokarkivService.sendPdfTilDokarkiv(any(), any(), any(), any(), any(), any(), any()) } returns journalpostId.right()

        opprettDokumenterOgArkiver().getOrFail().first shouldBe journalpostId

        coVerify(exactly = 1) { dokarkivService.sendPdfTilDokarkiv(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `journalfører også skygge-pdf fra pdfgenrs når den finnes`() = runTest {
        coEvery { pdfService.lagPdf(any()) } returns Pair(ByteArray(1), ByteArray(2)).right()
        coEvery { pdfService.konverterVedlegg(any()) } returns emptyList<no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg>().right()
        coEvery { dokarkivService.sendPdfTilDokarkiv(any(), any(), any(), any(), any(), any(), any()) } returns journalpostId.right()

        opprettDokumenterOgArkiver().getOrFail().first shouldBe journalpostId

        coVerify(exactly = 1) { dokarkivService.sendPdfTilDokarkiv(any(), any(), any(), any(), any(), any(), pdfgenrs = false) }
        coVerify(exactly = 1) { dokarkivService.sendPdfTilDokarkiv(any(), any(), any(), any(), any(), any(), pdfgenrs = true) }
    }

    @Test
    fun `feil ved pdf-generering stopper flyten før vedlegg og journalføring`() = runTest {
        coEvery { pdfService.lagPdf(any()) } returns HttpKlientError.UventetStatus(500, "nede", tomMetadata()).left()

        opprettDokumenterOgArkiver().leftOrNull()!! shouldBe KunneIkkeOpprettDokumenter.PdfGenereringFeilet

        coVerify(exactly = 0) { pdfService.konverterVedlegg(any()) }
        coVerify(exactly = 0) { dokarkivService.sendPdfTilDokarkiv(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `feil ved vedleggskonvertering stopper flyten før journalføring`() = runTest {
        coEvery { pdfService.lagPdf(any()) } returns Pair(ByteArray(1), null).right()
        coEvery { pdfService.konverterVedlegg(any()) } returns
            KunneIkkeKonvertereVedlegg.KallFeilet(HttpKlientError.UventetStatus(500, "nede", tomMetadata())).left()

        opprettDokumenterOgArkiver().leftOrNull()!! shouldBe KunneIkkeOpprettDokumenter.VedleggskonverteringFeilet

        coVerify(exactly = 0) { dokarkivService.sendPdfTilDokarkiv(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `ugyldig filformat på vedlegg gir VedleggskonverteringFeilet`() = runTest {
        coEvery { pdfService.lagPdf(any()) } returns Pair(ByteArray(1), null).right()
        coEvery { pdfService.konverterVedlegg(any()) } returns KunneIkkeKonvertereVedlegg.UgyldigFilformat("text/plain").left()

        opprettDokumenterOgArkiver().leftOrNull()!! shouldBe KunneIkkeOpprettDokumenter.VedleggskonverteringFeilet
    }

    @Test
    fun `feilet journalføring gir JournalføringFeilet`() = runTest {
        coEvery { pdfService.lagPdf(any()) } returns Pair(ByteArray(1), null).right()
        coEvery { pdfService.konverterVedlegg(any()) } returns emptyList<no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg>().right()
        coEvery { dokarkivService.sendPdfTilDokarkiv(any(), any(), any(), any(), any(), any(), any()) } returns
            KunneIkkeJournalføre.KallFeilet(HttpKlientError.UventetStatus(500, "nede", tomMetadata())).left()

        opprettDokumenterOgArkiver().leftOrNull()!! shouldBe KunneIkkeOpprettDokumenter.JournalføringFeilet
    }

    @Test
    fun `journalpost som ikke ble ferdigstilt gir JournalføringFeilet`() = runTest {
        coEvery { pdfService.lagPdf(any()) } returns Pair(ByteArray(1), null).right()
        coEvery { pdfService.konverterVedlegg(any()) } returns emptyList<no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg>().right()
        coEvery { dokarkivService.sendPdfTilDokarkiv(any(), any(), any(), any(), any(), any(), any()) } returns
            KunneIkkeJournalføre.IkkeFerdigstilt(journalpostId).left()

        opprettDokumenterOgArkiver().leftOrNull()!! shouldBe KunneIkkeOpprettDokumenter.JournalføringFeilet
    }

    @Test
    fun `feilet skygge-journalføring feiler hele flyten`() = runTest {
        coEvery { pdfService.lagPdf(any()) } returns Pair(ByteArray(1), ByteArray(2)).right()
        coEvery { pdfService.konverterVedlegg(any()) } returns emptyList<no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg>().right()
        coEvery { dokarkivService.sendPdfTilDokarkiv(any(), any(), any(), any(), any(), any(), pdfgenrs = false) } returns journalpostId.right()
        coEvery { dokarkivService.sendPdfTilDokarkiv(any(), any(), any(), any(), any(), any(), pdfgenrs = true) } returns
            KunneIkkeJournalføre.KallFeilet(HttpKlientError.UventetStatus(500, "nede", tomMetadata())).left()

        opprettDokumenterOgArkiver().leftOrNull()!! shouldBe KunneIkkeOpprettDokumenter.JournalføringFeilet
    }

    /** [HttpKlientMetadata] har bevisst ingen defaults, så testene fyller alle feltene eksplisitt. */
    private fun tomMetadata() = HttpKlientMetadata(
        rawRequestString = "",
        rawResponseString = null,
        requestHeaders = emptyMap(),
        responseHeaders = emptyMap(),
        statusCode = 500,
        attempts = 1,
        attemptDurations = emptyList(),
        totalDuration = ZERO,
        tidsstempler = HttpKlientTidsstempler.INGEN,
    )
}
