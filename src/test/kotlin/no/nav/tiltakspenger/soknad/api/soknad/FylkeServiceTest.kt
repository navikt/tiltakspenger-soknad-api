package no.nav.tiltakspenger.soknad.api.soknad

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FylkeServiceTest {
    @Test
    fun `brukersFylkeRutesTilTpsak - brukers kommunenummer skal rutes til tpsak - rutes til tpsak`() {
        val fylkeService = FylkeService()
        fylkeService.brukersFylkeRutesTilTpsak("1112", listOf("11")) shouldBe true
    }

    @Test
    fun `brukersFylkeRutesTilTpsak - brukers bydelsnummer skal rutes til tpsak - rutes til tpsak`() {
        val fylkeService = FylkeService()
        fylkeService.brukersFylkeRutesTilTpsak("110307", listOf("11")) shouldBe true
    }

    @Test
    fun `brukersFylkeRutesTilTpsak - brukers kommunenummer skal ikke rutes til tpsak - rutes ikke til tpsak`() {
        val fylkeService = FylkeService()
        fylkeService.brukersFylkeRutesTilTpsak("1112", listOf("18")) shouldBe false
    }

    @Test
    fun `brukersFylkeRutesTilTpsak - brukers bydelsnummer skal rutes til tpsak - rutes ikke til tpsak`() {
        val fylkeService = FylkeService()
        fylkeService.brukersFylkeRutesTilTpsak("110307", listOf("18")) shouldBe false
    }

    @Test
    fun `brukersFylkeRutesTilTpsak - brukers geografiske tilknytning mangler - rutes ikke til tpsak`() {
        val fylkeService = FylkeService()
        fylkeService.brukersFylkeRutesTilTpsak(null, listOf("18")) shouldBe false
    }

    @Test
    fun `brukersFylkeRutesTilTpsak - ingen fylker rutes til tpsak - rutes ikke til tpsak`() {
        val fylkeService = FylkeService()
        fylkeService.brukersFylkeRutesTilTpsak("1112", emptyList()) shouldBe false
    }
}
