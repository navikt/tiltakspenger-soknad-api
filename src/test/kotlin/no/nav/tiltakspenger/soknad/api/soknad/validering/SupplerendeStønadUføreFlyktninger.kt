package no.nav.tiltakspenger.soknad.api.soknad.validering

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.mockSupplerendestønadflyktninger
import no.nav.tiltakspenger.soknad.api.mockTiltak
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class SupplerendeStønadUføreFlyktninger {
    @Test
    fun `SupplerendeStønadUføreFlyktninger med gyldig periode skal validere ok`() {
        mockSpørsmålsbesvarelser(
            supplerendestønadflyktninger = mockSupplerendestønadflyktninger(
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
    fun `SupplerendeStønadUføreFlyktninger periode fra må være lik eller før fra dato`() {
        mockSpørsmålsbesvarelser(
            supplerendestønadflyktninger = mockSupplerendestønadflyktninger(
                mottar = true,
                periode = Periode(
                    fra = LocalDate.of(2025, 2, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
            mottarAndreUtbetalinger = true,
        ).valider(fixedClock) shouldContain "Perioden på SupplerendeStønadUføreFlyktninger er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `SupplerendeStønadUføreFlyktninger med mottar = false skal ikke ha en periode`() {
        mockSpørsmålsbesvarelser(
            supplerendestønadflyktninger = mockSupplerendestønadflyktninger(
                mottar = false,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
            mottarAndreUtbetalinger = true,
        ).valider(fixedClock) shouldContain "SupplerendeStønadUføreFlyktninger med mottar = false kan ikke ha noen periode"
    }

    @Test
    fun `SupplerendeStønadUføreFlyktninger periode til kan ikke være tidligere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            supplerendestønadflyktninger = mockSupplerendestønadflyktninger(
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
        ).valider(fixedClock) shouldContain "Perioden på SupplerendeStønadUføreFlyktninger er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `SupplerendeStønadUføreFlyktninger periode kan ikke være senere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            supplerendestønadflyktninger = mockSupplerendestønadflyktninger(
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
        ).valider(fixedClock) shouldContain "Perioden på SupplerendeStønadUføreFlyktninger er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `SupplerendeStønadUføreFlyktninger med mottar = true må ha en periode`() {
        mockSpørsmålsbesvarelser(
            supplerendestønadflyktninger = mockSupplerendestønadflyktninger(
                mottar = true,
                periode = null,
            ),
            mottarAndreUtbetalinger = true,
        ).valider(fixedClock) shouldContain "SupplerendeStønadUføreFlyktninger med mottar = true må ha periode"
    }
}
