package no.nav.tiltakspenger.soknad.api.soknad.validering

import io.kotest.matchers.collections.shouldContain
import no.nav.tiltakspenger.soknad.api.mockBarnetillegg
import no.nav.tiltakspenger.soknad.api.mockManueltRegistrertBarn
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class BarnetilleggTest {
    @Test
    fun `fornavn på manuelt registrerte barn, skal ikke overskride maksgrense på 25 tegn`() {
        mockSpørsmålsbesvarelser(
            barnetillegg = mockBarnetillegg(
                manueltRegistrerteBarnSøktBarnetilleggFor = listOf(
                    mockManueltRegistrertBarn(
                        fornavn = "Test Test Test Test Test Test Test Test",
                        mellomnavn = "Test",
                        etternavn = "a".toString().repeat(1001),
                    ),
                ),
            ),
        ).valider() shouldContain "Manuelt registrert barn er ugyldig: fornavn, mellomnavn eller etternavn overskrider maksgrense på 1000 tegn"
    }

    @Test
    fun `mellomnavn på manuelt registrerte barn, skal ikke overskride maksgrense på 25 tegn`() {
        mockSpørsmålsbesvarelser(
            barnetillegg = mockBarnetillegg(
                manueltRegistrerteBarnSøktBarnetilleggFor = listOf(
                    mockManueltRegistrertBarn(
                        fornavn = "Test",
                        mellomnavn = "Test Test Test Test Test Test Test Test",
                        etternavn = "a".toString().repeat(1001),
                    ),
                ),
            ),
        ).valider() shouldContain "Manuelt registrert barn er ugyldig: fornavn, mellomnavn eller etternavn overskrider maksgrense på 1000 tegn"
    }

    @Test
    fun `etternavn på manuelt registrerte barn, skal ikke overskride maksgrense på 1000 tegn`() {
        mockSpørsmålsbesvarelser(
            barnetillegg = mockBarnetillegg(
                manueltRegistrerteBarnSøktBarnetilleggFor = listOf(
                    mockManueltRegistrertBarn(
                        fornavn = "Test",
                        mellomnavn = "Test",
                        etternavn = "a".toString().repeat(1001),
                    ),
                ),
            ),
        ).valider() shouldContain "Manuelt registrert barn er ugyldig: fornavn, mellomnavn eller etternavn overskrider maksgrense på 1000 tegn"
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
                        fødselsdato = LocalDate.now().plusDays(1),
                    ),
                ),
            ),
        ).valider() shouldContain "Manuelt registrert barn er ugyldig: fødselsdato kan ikke registreres fram i tid"
    }
}
