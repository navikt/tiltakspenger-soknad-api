package no.nav.tiltakspenger.soknad.api.soknad

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.soknad.api.Configuration
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.mockTiltak
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering
import no.nav.tiltakspenger.soknad.api.pdl.Person
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class NySøknadServiceTest {
    private val søknadRepo = mockk<SøknadRepo>(relaxed = true)
    private val fylkeService = FylkeService()
    private val nySøknadService = NySøknadService(søknadRepo, fylkeService)
    private lateinit var kommando: NySøknadCommand

    private val gjennomforingerSomSkalTilTpsak = listOf("1234-5678")
    private val fylkerSomSkalTilTpsak = listOf("55", "56")

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
        val person = getPerson(gradering = AdressebeskyttelseGradering.UGRADERT)

        nySøknadService.getEier(kommando, person, gjennomforingerSomSkalTilTpsak, fylkerSomSkalTilTpsak) shouldBe Applikasjonseier.Arena
    }

    @Test
    fun `eier settes til Tiltakspenger i prod når bruker har tidligere søknader som eies av Tiltakspenger`() {
        every { Configuration.isProd() } returns true
        every { søknadRepo.hentBrukersSøknader(any(), Applikasjonseier.Tiltakspenger) } returns listOf(mockk())
        val person = getPerson(gradering = AdressebeskyttelseGradering.UGRADERT)

        nySøknadService.getEier(kommando, person, gjennomforingerSomSkalTilTpsak, fylkerSomSkalTilTpsak) shouldBe Applikasjonseier.Tiltakspenger
    }

    @Test
    fun `eier settes til Tiltakspenger i prod når kode6-bruker har tidligere søknader som eies av Tiltakspenger`() {
        every { Configuration.isProd() } returns true
        every { søknadRepo.hentBrukersSøknader(any(), Applikasjonseier.Tiltakspenger) } returns listOf(mockk())
        val person = getPerson(gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG)

        nySøknadService.getEier(kommando, person, gjennomforingerSomSkalTilTpsak, fylkerSomSkalTilTpsak) shouldBe Applikasjonseier.Tiltakspenger
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
        val person = getPerson(gradering = AdressebeskyttelseGradering.UGRADERT)

        nySøknadService.getEier(kommando, person, gjennomforingerSomSkalTilTpsak, fylkerSomSkalTilTpsak) shouldBe Applikasjonseier.Tiltakspenger
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
        val person = getPerson(gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND)

        nySøknadService.getEier(kommando, person, gjennomforingerSomSkalTilTpsak, fylkerSomSkalTilTpsak) shouldBe Applikasjonseier.Arena
    }

    @Test
    fun `eier settes til Tiltakspenger i prod når bruker tilhører fylke som rutes til tpsak`() {
        every { Configuration.isProd() } returns true
        every { søknadRepo.hentBrukersSøknader(any(), Applikasjonseier.Tiltakspenger) } returns emptyList()
        val person = getPerson(
            gradering = AdressebeskyttelseGradering.UGRADERT,
            geografiskTilknytning = "5510",
        )

        nySøknadService.getEier(kommando, person, gjennomforingerSomSkalTilTpsak, fylkerSomSkalTilTpsak) shouldBe Applikasjonseier.Tiltakspenger
    }

    @Test
    fun `eier settes til Tiltakspenger i dev`() {
        every { Configuration.isProd() } returns false
        val person = getPerson(gradering = AdressebeskyttelseGradering.UGRADERT)

        nySøknadService.getEier(kommando, person, gjennomforingerSomSkalTilTpsak, fylkerSomSkalTilTpsak) shouldBe Applikasjonseier.Tiltakspenger
    }

    @Test
    fun `eier settes til Tiltakspenger i dev for kode6-bruker`() {
        every { Configuration.isProd() } returns false
        val person = getPerson(gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG)

        nySøknadService.getEier(kommando, person, gjennomforingerSomSkalTilTpsak, fylkerSomSkalTilTpsak) shouldBe Applikasjonseier.Tiltakspenger
    }

    private fun getPerson(
        gradering: AdressebeskyttelseGradering = AdressebeskyttelseGradering.UGRADERT,
        geografiskTilknytning: String? = "1122",
    ): Person {
        return Person(
            fnr = Fnr.fromString("02058938710"),
            fornavn = "fornavn",
            mellomnavn = null,
            etternavn = "etternavn",
            adressebeskyttelseGradering = gradering,
            fødselsdato = LocalDate.now().minusYears(35),
            forelderBarnRelasjon = emptyList(),
            erDød = false,
            geografiskTilknytning = geografiskTilknytning,
        )
    }
}
