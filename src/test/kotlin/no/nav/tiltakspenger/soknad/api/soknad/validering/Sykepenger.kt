package no.nav.tiltakspenger.soknad.api.soknad.validering

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.mockSykepenger
import no.nav.tiltakspenger.soknad.api.mockTiltak
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class Sykepenger {
    @Test
    fun `Sykepenger med gyldig periode skal validere ok`() {
        mockSpørsmålsbesvarelser(
            sykepenger = mockSykepenger(
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
            mottarAndreUtbetalinger = true,
        ).valider(fixedClock) shouldBe emptyList()
    }

    @Test
    fun `Sykepenger periode fra må være lik eller før fra dato`() {
        mockSpørsmålsbesvarelser(
            sykepenger = mockSykepenger(
                mottar = true,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 2),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
            mottarAndreUtbetalinger = true,
        ).valider(fixedClock) shouldContain "Perioden på sykepenger er ugyldig. Fra-dato må være tidligere enn, eller lik, til-dato."
    }

    @Test
    fun `Sykepenger med mottar = false skal ikke ha en periode`() {
        mockSpørsmålsbesvarelser(
            sykepenger = mockSykepenger(
                mottar = false,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
            mottarAndreUtbetalinger = true,
        ).valider(fixedClock) shouldContain "Sykepenger med mottar = false kan ikke ha noen periode"
    }

    @Test
    fun `Sykepenger periode til kan ikke være tidligere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            sykepenger = mockSykepenger(
                mottar = true,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 2),
                ),
            ),
            tiltak = mockTiltak(
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 2),
                    til = LocalDate.of(2025, 1, 3),
                ),
            ),
            mottarAndreUtbetalinger = true,
        ).valider(fixedClock) shouldContain "Perioden på sykepenger er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `sykepenger periode kan ikke være senere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            sykepenger = mockSykepenger(
                mottar = true,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 2),
                    til = LocalDate.of(2025, 1, 3),
                ),
            ),
            tiltak = mockTiltak(
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 2),
                ),
            ),
            mottarAndreUtbetalinger = true,
        ).valider(fixedClock) shouldContain "Perioden på sykepenger er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `Sykepenger med mottar = true må ha en periode`() {
        mockSpørsmålsbesvarelser(
            sykepenger = mockSykepenger(
                mottar = true,
                periode = null,
            ),
            mottarAndreUtbetalinger = true,
        ).valider(fixedClock) shouldContain "Sykepenger med mottar = true må ha periode"
    }
}
