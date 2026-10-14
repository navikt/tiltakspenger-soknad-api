package no.nav.tiltakspenger.soknad.api.soknad.validering

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.mockBarnetillegg
import no.nav.tiltakspenger.soknad.api.mockManueltRegistrertBarn
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BarnetilleggTest {
    @Test
    fun `fornavn på manuelt registrerte barn, skal ikke overskride maksgrense på 25 tegn`() {
        mockSpørsmålsbesvarelser(
            barnetillegg = mockBarnetillegg(
                manueltRegistrerteBarnSøktBarnetilleggFor = listOf(
                    mockManueltRegistrertBarn(
                        fornavn = "Test Test Test Test Test Test Test Test",
                        mellomnavn = "Test",
                        etternavn = "a".repeat(1001),
                    ),
                ),
            ),
        ).valider(fixedClock) shouldContain "Manuelt registrert barn er ugyldig: fornavn, mellomnavn eller etternavn overskrider maksgrense på 1000 tegn"
    }

    @Test
    fun `mellomnavn på manuelt registrerte barn, skal ikke overskride maksgrense på 25 tegn`() {
        mockSpørsmålsbesvarelser(
            barnetillegg = mockBarnetillegg(
                manueltRegistrerteBarnSøktBarnetilleggFor = listOf(
                    mockManueltRegistrertBarn(
                        fornavn = "Test",
                        mellomnavn = "Test Test Test Test Test Test Test Test",
                        etternavn = "a".repeat(1001),
                    ),
                ),
            ),
        ).valider(fixedClock) shouldContain "Manuelt registrert barn er ugyldig: fornavn, mellomnavn eller etternavn overskrider maksgrense på 1000 tegn"
    }

    @Test
    fun `etternavn på manuelt registrerte barn, skal ikke overskride maksgrense på 1000 tegn`() {
        mockSpørsmålsbesvarelser(
            barnetillegg = mockBarnetillegg(
                manueltRegistrerteBarnSøktBarnetilleggFor = listOf(
                    mockManueltRegistrertBarn(
                        fornavn = "Test",
                        mellomnavn = "Test",
                        etternavn = "a".repeat(1001),
                    ),
                ),
            ),
        ).valider(fixedClock) shouldContain "Manuelt registrert barn er ugyldig: fornavn, mellomnavn eller etternavn overskrider maksgrense på 1000 tegn"
    }

    @Test
    fun `fødselsdato på manuelt registrerte barn skal ikke kunne settes fram i tid`() {
        mockSpørsmålsbesvarelser(
            barnetillegg = mockBarnetillegg(
                manueltRegistrerteBarnSøktBarnetilleggFor = listOf(
                    mockManueltRegistrertBarn(
                        fornavn = "Test",
                        mellomnavn = "Test",
                        etternavn = "Test",
                        fødselsdato = LocalDate.now(fixedClock).plusDays(1),
                    ),
                ),
            ),
        ).valider(fixedClock) shouldContain "Manuelt registrert barn er ugyldig: fødselsdato kan ikke registreres fram i tid"
    }

    @Test
    fun `nei på å søke barnetillegg kan ikke ha barn`() {
        mockSpørsmålsbesvarelser(
            barnetillegg = mockBarnetillegg(
                søkerOmBarnetillegg = false,
                manueltRegistrerteBarnSøktBarnetilleggFor = emptyList(),
            ),
        ).valider(fixedClock) shouldContain "Bruker har svart nei på å søke barnetillegg, men søknaden har barn"
    }

    @Test
    fun `ja på å søke barnetillegg må ha minst ett barn`() {
        mockSpørsmålsbesvarelser(
            barnetillegg = mockBarnetillegg(
                søkerOmBarnetillegg = true,
                manueltRegistrerteBarnSøktBarnetilleggFor = emptyList(),
                registrerteBarnSøktBarnetilleggFor = emptyList(),
            ),
        ).valider(fixedClock) shouldContain "Bruker har svart ja på å søke barnetillegg, men søknaden har ingen barn"
    }

    @Test
    fun `svar på å søke barnetillegg som stemmer med barnelistene er gyldig`() {
        mockSpørsmålsbesvarelser(
            barnetillegg = mockBarnetillegg(
                søkerOmBarnetillegg = false,
                manueltRegistrerteBarnSøktBarnetilleggFor = emptyList(),
                registrerteBarnSøktBarnetilleggFor = emptyList(),
            ),
        ).valider(fixedClock) shouldBe emptyList()
        mockSpørsmålsbesvarelser(
            barnetillegg = mockBarnetillegg(
                søkerOmBarnetillegg = true,
                manueltRegistrerteBarnSøktBarnetilleggFor = emptyList(),
            ),
        ).valider(fixedClock) shouldBe emptyList()
    }

    @Test
    fun `søknader uten svar på å søke barnetillegg valideres som før`() {
        mockSpørsmålsbesvarelser(
            barnetillegg = mockBarnetillegg(
                søkerOmBarnetillegg = null,
                manueltRegistrerteBarnSøktBarnetilleggFor = emptyList(),
                registrerteBarnSøktBarnetilleggFor = emptyList(),
            ),
        ).valider(fixedClock) shouldBe emptyList()
    }

    @Test
    fun `nei på å søke barnetillegg kan heller ikke ha manuelt registrerte barn`() {
        mockSpørsmålsbesvarelser(
            barnetillegg = mockBarnetillegg(
                søkerOmBarnetillegg = false,
                registrerteBarnSøktBarnetilleggFor = emptyList(),
            ),
        ).valider(fixedClock) shouldBe listOf("Bruker har svart nei på å søke barnetillegg, men søknaden har barn")
    }

    @Test
    fun `ja på å søke barnetillegg med bare manuelt registrerte barn er gyldig`() {
        mockSpørsmålsbesvarelser(
            barnetillegg = mockBarnetillegg(
                søkerOmBarnetillegg = true,
                registrerteBarnSøktBarnetilleggFor = emptyList(),
            ),
        ).valider(fixedClock) shouldBe emptyList()
    }
}
