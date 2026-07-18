package no.nav.tiltakspenger.soknad.api.soknad.validering

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.mockGjenlevendepensjon
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.mockTiltak
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class Gjenlevendepensjon {
    @Test
    fun `gjenlevendepensjon med gyldig periode skal validere ok`() {
        mockSpørsmålsbesvarelser(
            gjenlevendepensjon = mockGjenlevendepensjon(
                mottar = true,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
            tiltak = mockTiltak(
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
        ).valider(fixedClock) shouldBe emptyList()
    }

    @Test
    fun `Gjenlevendepensjon periode fra må være lik eller før fra dato`() {
        mockSpørsmålsbesvarelser(
            mottarAndreUtbetalinger = true,
            gjenlevendepensjon = mockGjenlevendepensjon(
                mottar = true,
                periode = Periode(
                    fra = LocalDate.of(2025, 2, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
        ).valider(fixedClock) shouldContain "Perioden på gjenlevendepensjon er ugyldig. Fra-dato må være tidligere enn, eller lik, til-dato."
    }

    @Test
    fun `Gjenlevendepensjon med mottar = false skal ikke ha en periode`() {
        mockSpørsmålsbesvarelser(
            mottarAndreUtbetalinger = true,
            gjenlevendepensjon = mockGjenlevendepensjon(
                mottar = false,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
        ).valider(fixedClock) shouldContain "Gjenlevendepensjon med mottar = false kan ikke ha noen periode"
    }

    @Test
    fun `Gjenlevendepensjon periode til kan ikke være tidligere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            mottarAndreUtbetalinger = true,
            gjenlevendepensjon = mockGjenlevendepensjon(
                mottar = true,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 31),
                ),
            ),
            tiltak = mockTiltak(
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 31),
                    til = LocalDate.of(2025, 2, 1),
                ),
            ),
        ).valider(fixedClock) shouldContain "Perioden på gjenlevendepensjon er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `Gjenlevendepensjon periode kan ikke være senere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            mottarAndreUtbetalinger = true,
            gjenlevendepensjon = mockGjenlevendepensjon(
                mottar = true,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 31),
                    til = LocalDate.of(2025, 2, 1),
                ),
            ),
            tiltak = mockTiltak(
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 31),
                ),
            ),
        ).valider(fixedClock) shouldContain "Perioden på gjenlevendepensjon er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `Gjenlevendepensjon med mottar = true må ha en periode`() {
        mockSpørsmålsbesvarelser(
            mottarAndreUtbetalinger = true,
            gjenlevendepensjon = mockGjenlevendepensjon(
                mottar = true,
                periode = null,
            ),
        ).valider(fixedClock) shouldContain "Gjenlevendepensjon med mottar = true må ha periode"
    }
}
