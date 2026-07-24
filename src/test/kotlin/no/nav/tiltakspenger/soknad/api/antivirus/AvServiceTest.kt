package no.nav.tiltakspenger.soknad.api.antivirus

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import org.junit.jupiter.api.Test

// TODO jah: ClamAvClient mockes her.
//  I denne fila kan man heller bruke en fake og hvis man får til endre fra service test til e2e.
//  Går an å ta en titt hvordan andre repo gjør det.
//  Kanskje vi kan fyre den opp med testcontainers.
class AvServiceTest {
    private val clamAvClient = mockk<ClamAvClient>()
    private val avService = AvService(clamAvClient)

    private val vedlegg = listOf(
        Vedlegg(filnavn = "fil.pdf", contentType = "application/pdf", dokument = ByteArray(1)),
    )

    @Test
    fun `rene vedlegg passerer virussjekken`() = runTest {
        coEvery { clamAvClient.scan(any()) } returns listOf(AvSjekkResultat("fil.pdf", Status.OK))

        shouldNotThrowAny { avService.gjørVirussjekkAvVedlegg(vedlegg) }
    }

    @Test
    fun `funn av skadevare kaster MalwareFoundException`() = runTest {
        coEvery { clamAvClient.scan(any()) } returns listOf(AvSjekkResultat("fil.pdf", Status.FOUND))

        shouldThrow<MalwareFoundException> { avService.gjørVirussjekkAvVedlegg(vedlegg) }
    }

    @Test
    fun `feil under virusscan kaster RuntimeException`() = runTest {
        coEvery { clamAvClient.scan(any()) } returns listOf(AvSjekkResultat("fil.pdf", Status.ERROR))

        shouldThrow<RuntimeException> { avService.gjørVirussjekkAvVedlegg(vedlegg) }
    }
}
