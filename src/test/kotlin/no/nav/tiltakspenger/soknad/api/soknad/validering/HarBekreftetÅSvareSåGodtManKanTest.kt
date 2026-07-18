package no.nav.tiltakspenger.soknad.api.soknad.validering

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import org.junit.jupiter.api.Test

internal class HarBekreftetÅSvareSåGodtManKanTest {

    @Test
    fun `hvis bruker ikke har bekreftet at man vil svare så godt man kan, skal vi ikke godta søknaden`() {
        mockSpørsmålsbesvarelser(harBekreftetÅSvareSåGodtManKan = false)
            .valider(fixedClock) shouldContain "Bruker må bekrefte å svare så godt man kan"
    }

    @Test
    fun `hvis bruker har bekreftet alle opplysninger, skal vi godta søknaden`() {
        mockSpørsmålsbesvarelser(harBekreftetÅSvareSåGodtManKan = true)
            .valider(fixedClock) shouldBe emptyList()
    }
}
