package no.nav.tiltakspenger.soknad.api.soknad.validering

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.mockPensjonsordning
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.mockTiltak
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class PensjonsordningTest {
    @Test
    fun `Pensjonsordning med gyldig periode skal validere ok`() {
        mockSpørsmålsbesvarelser(
            pensjonsordning = mockPensjonsordning(
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
    fun `Pensjonsordning periode fra må være lik eller før fra dato`() {
        mockSpørsmålsbesvarelser(
            mottarAndreUtbetalinger = true,
            pensjonsordning = mockPensjonsordning(
                mottar = true,
                periode = Periode(
                    fra = LocalDate.of(2025, 2, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
        ).valider(fixedClock) shouldContain "Perioden på pensjonsordning er ugyldig. Fra-dato må være tidligere enn, eller lik, til-dato."
    }

    @Test
    fun `Pensjonsordning med mottar = false skal ikke ha en periode`() {
        mockSpørsmålsbesvarelser(
            mottarAndreUtbetalinger = true,
            pensjonsordning = mockPensjonsordning(
                mottar = false,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
        ).valider(fixedClock) shouldContain "Pensjonsordning med mottar = false kan ikke ha noen periode"
    }

    @Test
    fun `Pensjonsordning periode til kan ikke være tidligere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            mottarAndreUtbetalinger = true,
            pensjonsordning = mockPensjonsordning(
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
        ).valider(fixedClock) shouldContain "Perioden på pensjonsordning er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `Pensjonsordning periode kan ikke være senere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            mottarAndreUtbetalinger = true,
            pensjonsordning = mockPensjonsordning(
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
        ).valider(fixedClock) shouldContain "Perioden på pensjonsordning er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `Pensjonsordning med mottar = true må ha en periode`() {
        mockSpørsmålsbesvarelser(
            mottarAndreUtbetalinger = true,
            pensjonsordning = mockPensjonsordning(
                mottar = true,
                periode = null,
            ),
        ).valider(fixedClock) shouldContain "Pensjonsordning med mottar = true må ha periode"
    }
}
