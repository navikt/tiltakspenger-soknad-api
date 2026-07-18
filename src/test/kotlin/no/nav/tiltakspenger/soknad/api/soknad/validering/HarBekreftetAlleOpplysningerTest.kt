package no.nav.tiltakspenger.soknad.api.soknad.validering

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import org.junit.jupiter.api.Test

internal class HarBekreftetAlleOpplysningerTest {

    @Test
    fun `hvis bruker ikke har bekreftet alle opplysninger, skal vi ikke godta søknaden`() {
        mockSpørsmålsbesvarelser(harBekreftetAlleOpplysninger = false)
            .valider(fixedClock) shouldContain "Bruker må bekrefte å ha oppgitt riktige opplysninger"
    }

    @Test
    fun `hvis bruker har bekreftet alle opplysninger, skal vi godta søknaden`() {
        mockSpørsmålsbesvarelser(harBekreftetAlleOpplysninger = true)
            .valider(fixedClock) shouldBe emptyList()
    }
}
