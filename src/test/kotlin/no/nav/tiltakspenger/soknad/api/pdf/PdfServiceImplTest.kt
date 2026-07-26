package no.nav.tiltakspenger.soknad.api.pdf

import arrow.core.right
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.soknad.api.domain.Søknad
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import org.junit.jupiter.api.Test

// TODO jah: PdfGenerator mockes her for en ren delegeringstest.
// Kunne vært dekket e2e via /soknad-ruten med en reell PdfClient (pdfgen-fake) i stedet for mock.
class PdfServiceImplTest {
    private val pdfGenerator = mockk<PdfGenerator>()
    private val pdfService = PdfServiceImpl(pdfGenerator)

    @Test
    fun `delegerer pdf-generering og vedleggskonvertering til generatoren`() = runTest {
        val søknad = Søknad.toSøknad(
            id = "id",
            acr = "acr",
            spørsmålsbesvarelser = mockSpørsmålsbesvarelser(),
            vedleggsnavn = emptyList(),
            fnr = "12345678910",
            fornavn = "Fornavn",
            etternavn = "Etternavn",
            innsendingTidspunkt = nå(fixedClock),
        )
        val vedlegg = listOf(Vedlegg(filnavn = "fil.pdf", contentType = "application/pdf", dokument = ByteArray(1)))
        val pdf = ByteArray(2)
        coEvery { pdfGenerator.genererPdf(søknad) } returns Pair(pdf, null).right()
        coEvery { pdfGenerator.konverterVedlegg(vedlegg) } returns vedlegg.right()

        pdfService.lagPdf(søknad).getOrFail() shouldBe Pair(pdf, null)
        pdfService.konverterVedlegg(vedlegg).getOrFail() shouldBe vedlegg
    }
}
