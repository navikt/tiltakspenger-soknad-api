package no.nav.tiltakspenger.soknad.api.soknad

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import no.nav.tiltakspenger.soknad.api.Configuration
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.mockTiltak
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class NySøknadServiceTest {
    private val søknadRepo = mockk<SøknadRepo>(relaxed = true)
    private val nySøknadService = NySøknadService(søknadRepo)
    private lateinit var kommando: NySøknadCommand

    @BeforeEach
    fun setUp() {
        kommando = NySøknadCommand(
            brukersBesvarelser = mockSpørsmålsbesvarelser(),
            acr = "Level4",
            fnr = "12345678910",
            vedlegg = listOf(),
            innsendingTidspunkt = LocalDateTime.now(),
        )
        mockkObject(Configuration)
    }

    @Test
    fun `eier settes til Arena i prod når bruker ikke har tidligere søknader som eies av Tiltakspenger`() {
        every { Configuration.isProd() } returns true
        every { søknadRepo.hentBrukersSøknader(any(), Applikasjonseier.Tiltakspenger) } returns emptyList()

        val resultat = nySøknadService.nySøknad(kommando)

        verify { søknadRepo.lagre(match { it.eier == Applikasjonseier.Arena }) }
        resultat.isRight()
    }

    @Test
    fun `eier settes til Tiltakspenger i prod når bruker har tidligere søknader som eies av Tiltakspenger`() {
        every { Configuration.isProd() } returns true
        every { søknadRepo.hentBrukersSøknader(any(), Applikasjonseier.Tiltakspenger) } returns listOf(mockk())

        val resultat = nySøknadService.nySøknad(kommando)

        verify { søknadRepo.lagre(match { it.eier == Applikasjonseier.Tiltakspenger }) }
        resultat.isRight()
    }

    @Test
    fun `eier settes til Tiltakspenger i prod når bruker er forhåndsgodkjent`() {
        every { Configuration.isProd() } returns true
        every { søknadRepo.hentBrukersSøknader(any(), Applikasjonseier.Tiltakspenger) } returns emptyList()
        kommando = NySøknadCommand(
            brukersBesvarelser = mockSpørsmålsbesvarelser(
                tiltak = mockTiltak(
                    aktivitetId = "fda6f295-201e-4522-b94e-99d54b537f94",
                ),
            ),
            acr = "Level4",
            fnr = "12345678910",
            vedlegg = listOf(),
            innsendingTidspunkt = LocalDateTime.now(),
        )

        val resultat = nySøknadService.nySøknad(kommando)

        verify { søknadRepo.lagre(match { it.eier == Applikasjonseier.Tiltakspenger }) }
        resultat.isRight()
    }

    @Test
    fun `eier settes til Tiltakspenger i dev`() {
        every { Configuration.isProd() } returns false

        val resultat = nySøknadService.nySøknad(kommando)
        verify { søknadRepo.lagre(match { it.eier == Applikasjonseier.Tiltakspenger }) }
        resultat.isRight()
    }
}
