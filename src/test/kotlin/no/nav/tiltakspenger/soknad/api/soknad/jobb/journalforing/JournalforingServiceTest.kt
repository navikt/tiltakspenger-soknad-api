package no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforing

import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivService
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.pdf.PdfService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// TODO jah: Hele journalførings-flyten mockes her (PdfService + DokarkivService).
//  Bytt i størst mulig grad til e2e-test og fake kun klientene
class JournalforingServiceTest {
    private val pdfService = mockk<PdfService>()
    private val dokarkivService = mockk<DokarkivService>()
    private val journalforingService = JournalforingService(pdfService, dokarkivService)
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
        coEvery { pdfService.lagPdf(any()) } returns Pair(ByteArray(1), null)
        coEvery { pdfService.konverterVedlegg(any()) } returns emptyList()
        coEvery { dokarkivService.sendPdfTilDokarkiv(any(), any(), any(), any(), any(), any(), any(), any()) } returns journalpostId

        opprettDokumenterOgArkiver().first shouldBe journalpostId

        coVerify(exactly = 1) { dokarkivService.sendPdfTilDokarkiv(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `journalfører også skygge-pdf fra pdfgenrs når den finnes`() = runTest {
        coEvery { pdfService.lagPdf(any()) } returns Pair(ByteArray(1), ByteArray(2))
        coEvery { pdfService.konverterVedlegg(any()) } returns emptyList()
        coEvery { dokarkivService.sendPdfTilDokarkiv(any(), any(), any(), any(), any(), any(), any(), any()) } returns journalpostId

        opprettDokumenterOgArkiver().first shouldBe journalpostId

        coVerify(exactly = 1) { dokarkivService.sendPdfTilDokarkiv(any(), any(), any(), any(), any(), any(), any(), pdfgenrs = false) }
        coVerify(exactly = 1) { dokarkivService.sendPdfTilDokarkiv(any(), any(), any(), any(), any(), any(), any(), pdfgenrs = true) }
    }
}
