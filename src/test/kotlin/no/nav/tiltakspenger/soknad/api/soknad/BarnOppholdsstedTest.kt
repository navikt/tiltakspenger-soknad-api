package no.nav.tiltakspenger.soknad.api.soknad

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.libs.json.serialize
import no.nav.tiltakspenger.soknad.api.mockManueltRegistrertBarn
import no.nav.tiltakspenger.soknad.api.mockRegistrertBarn
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

/**
 * Eldre søknadsfrontender sendte bare `oppholdInnenforEøs`, og eldre søknader i basen har bare det feltet.
 * Når søknaden har `oppholdssted`, skal `oppholdInnenforEøs` avledes, slik at PDF-en og saksbehandling-api får det de forventer.
 */
class BarnOppholdsstedTest {

    @Test
    fun `barn med bare oppholdInnenforEøs får ikke oppholdssted`() {
        @Language("JSON")
        val json = """
            {
              "fnr": null,
              "fornavn": "Test",
              "mellomnavn": null,
              "etternavn": "Testesen",
              "fødselsdato": "2020-01-01",
              "oppholdInnenforEøs": false
            }
        """.trimIndent()

        val barn = deserialize<RegistrertBarn>(json)

        barn.oppholdInnenforEøs shouldBe false
        barn.oppholdssted shouldBe null
    }

    @Test
    fun `oppholdInnenforEøs avledes fra oppholdssted`() {
        mockRegistrertBarn(oppholdInnenforEøs = null, oppholdssted = Oppholdssted.NORGE).oppholdInnenforEøs shouldBe true
        mockRegistrertBarn(oppholdInnenforEøs = null, oppholdssted = Oppholdssted.ANNET_EØS_LAND).oppholdInnenforEøs shouldBe true
        mockRegistrertBarn(oppholdInnenforEøs = null, oppholdssted = Oppholdssted.IKKE_EØS_LAND).oppholdInnenforEøs shouldBe false
        mockManueltRegistrertBarn(oppholdInnenforEøs = null, oppholdssted = Oppholdssted.NORGE).oppholdInnenforEøs shouldBe true
        mockManueltRegistrertBarn(oppholdInnenforEøs = null, oppholdssted = Oppholdssted.IKKE_EØS_LAND).oppholdInnenforEøs shouldBe false
    }

    @Test
    fun `barn med oppholdssted deserialiseres og serialiseres med begge feltene`() {
        @Language("JSON")
        val json = """
            {
              "fornavn": "Test",
              "mellomnavn": null,
              "etternavn": "Testesen",
              "fødselsdato": "2020-01-01",
              "oppholdssted": "ANNET_EØS_LAND"
            }
        """.trimIndent()

        val barn = deserialize<ManueltRegistrertBarn>(json)

        barn.oppholdssted shouldBe Oppholdssted.ANNET_EØS_LAND
        barn.oppholdInnenforEøs shouldBe true
        val serialisert = serialize(barn)
        serialisert shouldContain "\"oppholdssted\":\"ANNET_EØS_LAND\""
        serialisert shouldContain "\"oppholdInnenforEøs\":true"
    }

    @Test
    fun `samsvarende oppholdInnenforEøs og oppholdssted godtas`() {
        mockRegistrertBarn(oppholdInnenforEøs = false, oppholdssted = Oppholdssted.IKKE_EØS_LAND).oppholdInnenforEøs shouldBe false
    }

    @Test
    fun `motstridende oppholdInnenforEøs og oppholdssted avvises`() {
        shouldThrowWithMessage<IllegalArgumentException>("oppholdInnenforEøs=true stemmer ikke med oppholdssted=IKKE_EØS_LAND") {
            mockRegistrertBarn(oppholdInnenforEøs = true, oppholdssted = Oppholdssted.IKKE_EØS_LAND)
        }
    }

    @Test
    fun `barn uten noe svar om opphold avvises`() {
        shouldThrowWithMessage<IllegalArgumentException>("Barnet må ha enten oppholdssted eller oppholdInnenforEøs") {
            mockManueltRegistrertBarn(oppholdInnenforEøs = null, oppholdssted = null)
        }
    }

    @Test
    fun `barn med ulikt oppholdssted er ulike`() {
        val norge = mockRegistrertBarn(fnr = "fnr", oppholdInnenforEøs = null, oppholdssted = Oppholdssted.NORGE)
        val annetEøsLand = mockRegistrertBarn(fnr = "fnr", oppholdInnenforEøs = null, oppholdssted = Oppholdssted.ANNET_EØS_LAND)

        (norge == annetEøsLand) shouldBe false
        (norge == mockRegistrertBarn(fnr = "fnr", oppholdInnenforEøs = true, oppholdssted = Oppholdssted.NORGE)) shouldBe true
        (mockManueltRegistrertBarn(oppholdssted = Oppholdssted.NORGE) == mockManueltRegistrertBarn(oppholdssted = Oppholdssted.ANNET_EØS_LAND)) shouldBe false
    }
}
