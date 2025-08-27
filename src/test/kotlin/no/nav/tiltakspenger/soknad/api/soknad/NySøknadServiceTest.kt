package no.nav.tiltakspenger.soknad.api.soknad

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import no.nav.tiltakspenger.soknad.api.Configuration
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.mockTiltak
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class NySøknadServiceTest {
    private val søknadRepo = mockk<SøknadRepo>(relaxed = true)
    private val nySøknadService = NySøknadService(søknadRepo)
    private lateinit var kommando: NySøknadCommand

    private val gjennomforingerSomSkalTilTpsak = listOf("1234-5678")

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

        nySøknadService.getEier(kommando, AdressebeskyttelseGradering.UGRADERT, gjennomforingerSomSkalTilTpsak) shouldBe Applikasjonseier.Arena
    }

    @Test
    fun `eier settes til Tiltakspenger i prod når bruker har tidligere søknader som eies av Tiltakspenger`() {
        every { Configuration.isProd() } returns true
        every { søknadRepo.hentBrukersSøknader(any(), Applikasjonseier.Tiltakspenger) } returns listOf(mockk())

        nySøknadService.getEier(kommando, AdressebeskyttelseGradering.UGRADERT, gjennomforingerSomSkalTilTpsak) shouldBe Applikasjonseier.Tiltakspenger
    }

    @Test
    fun `eier settes til Tiltakspenger i prod når kode6-bruker har tidligere søknader som eies av Tiltakspenger`() {
        every { Configuration.isProd() } returns true
        every { søknadRepo.hentBrukersSøknader(any(), Applikasjonseier.Tiltakspenger) } returns listOf(mockk())

        nySøknadService.getEier(kommando, AdressebeskyttelseGradering.STRENGT_FORTROLIG, gjennomforingerSomSkalTilTpsak) shouldBe Applikasjonseier.Tiltakspenger
    }

    @Test
    fun `eier settes til Tiltakspenger i prod når bruker deltar på forhåndsgodkjent tiltak`() {
        every { Configuration.isProd() } returns true
        every { søknadRepo.hentBrukersSøknader(any(), Applikasjonseier.Tiltakspenger) } returns emptyList()
        kommando = NySøknadCommand(
            brukersBesvarelser = mockSpørsmålsbesvarelser(
                tiltak = mockTiltak(
                    gjennomforingId = "1234-5678",
                ),
            ),
            acr = "Level4",
            fnr = "12345678910",
            vedlegg = listOf(),
            innsendingTidspunkt = LocalDateTime.now(),
        )

        nySøknadService.getEier(kommando, AdressebeskyttelseGradering.UGRADERT, gjennomforingerSomSkalTilTpsak) shouldBe Applikasjonseier.Tiltakspenger
    }

    @Test
    fun `eier settes til Arena i prod når kode6-bruker deltar på forhåndsgodkjent tiltak`() {
        every { Configuration.isProd() } returns true
        every { søknadRepo.hentBrukersSøknader(any(), Applikasjonseier.Tiltakspenger) } returns emptyList()
        kommando = NySøknadCommand(
            brukersBesvarelser = mockSpørsmålsbesvarelser(
                tiltak = mockTiltak(
                    gjennomforingId = "1234-5678",
                ),
            ),
            acr = "Level4",
            fnr = "12345678910",
            vedlegg = listOf(),
            innsendingTidspunkt = LocalDateTime.now(),
        )

        nySøknadService.getEier(kommando, AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND, gjennomforingerSomSkalTilTpsak) shouldBe Applikasjonseier.Arena
    }

    @Test
    fun `eier settes til Tiltakspenger i dev`() {
        every { Configuration.isProd() } returns false

        nySøknadService.getEier(kommando, AdressebeskyttelseGradering.UGRADERT, gjennomforingerSomSkalTilTpsak) shouldBe Applikasjonseier.Tiltakspenger
    }

    @Test
    fun `eier settes til Tiltakspenger i dev for kode6-bruker`() {
        every { Configuration.isProd() } returns false

        nySøknadService.getEier(kommando, AdressebeskyttelseGradering.STRENGT_FORTROLIG, gjennomforingerSomSkalTilTpsak) shouldBe Applikasjonseier.Tiltakspenger
    }
}
