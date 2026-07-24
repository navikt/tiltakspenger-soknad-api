package no.nav.tiltakspenger.soknad.api.soknad

import arrow.core.left
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.soknad.api.Configuration
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import org.junit.jupiter.api.AfterEach
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
            innsendingTidspunkt = nå(fixedClock),
        )
        // TODO jah: NySøknadService leser Configuration.isProd() direkte, så vi mockkObject-er singletonen.
        // Injiser profil/eier-avgjørelsen i stedet, så mock og global tilstand forsvinner.
        mockkObject(Configuration)
    }

    @AfterEach
    fun tearDown() {
        // mockkObject muterer den globale Configuration-singletonen; unmock så den ikke lekker til andre testklasser.
        unmockkObject(Configuration)
    }

    @Test
    fun `eier settes til Tiltakspenger i dev`() {
        every { Configuration.isProd() } returns false
        val resultat = nySøknadService.nySøknad(kommando)

        verify { søknadRepo.lagre(match { it.eier == Applikasjonseier.Tiltakspenger }) }
        resultat.isRight()
    }

    @Test
    fun `eier settes til Tiltakspenger i prod`() {
        every { Configuration.isProd() } returns true
        val resultat = nySøknadService.nySøknad(kommando)

        verify { søknadRepo.lagre(match { it.eier == Applikasjonseier.Tiltakspenger }) }
        resultat.isRight()
    }

    @Test
    fun `feil under lagring gir KunneIkkeLagreSøknad`() {
        every { Configuration.isProd() } returns false
        every { søknadRepo.lagre(any()) } throws RuntimeException("databasen er nede")

        val resultat = nySøknadService.nySøknad(kommando)

        resultat shouldBe KunneIkkeMottaNySøknad.KunneIkkeLagreSøknad.left()
    }
}
