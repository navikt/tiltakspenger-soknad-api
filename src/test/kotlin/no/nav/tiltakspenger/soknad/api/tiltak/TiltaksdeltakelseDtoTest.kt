package no.nav.tiltakspenger.soknad.api.tiltak

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.TiltakTypeDTO
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TiltaksdeltakelseDtoTest {
    private val idag = LocalDate.now(fixedClock)

    private fun deltakelse(fra: LocalDate?, til: LocalDate?) = TiltaksdeltakelseDto(
        aktivitetId = "id",
        type = TiltakTypeDTO.ARBTREN,
        typeNavn = "Arbeidstrening",
        arenaRegistrertPeriode = Deltakelsesperiode(fra = fra, til = til),
        arrangør = "arrangør",
        gjennomforingId = "gjennomforingId",
        visningsnavn = null,
    )

    @Test
    fun `deltakelse uten fradato er alltid innenfor relevant tidsrom`() {
        deltakelse(fra = null, til = null).erInnenforRelevantTidsrom(fixedClock) shouldBe true
    }

    @Test
    fun `deltakelse med kun fradato er innenfor når fradatoen er mellom 6 mnd tilbake og 2 mnd frem`() {
        deltakelse(fra = idag, til = null).erInnenforRelevantTidsrom(fixedClock) shouldBe true
        deltakelse(fra = idag.minusMonths(7), til = null).erInnenforRelevantTidsrom(fixedClock) shouldBe false
    }

    @Test
    fun `deltakelse med full periode er innenfor når perioden overlapper det relevante tidsrommet`() {
        deltakelse(fra = idag.minusMonths(1), til = idag.plusMonths(1)).erInnenforRelevantTidsrom(fixedClock) shouldBe true
        deltakelse(fra = idag.minusMonths(9), til = idag.minusMonths(7)).erInnenforRelevantTidsrom(fixedClock) shouldBe false
    }
}
