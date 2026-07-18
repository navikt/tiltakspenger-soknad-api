package no.nav.tiltakspenger.soknad.api.soknad.validering

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.mockSupplerendestønadover67
import no.nav.tiltakspenger.soknad.api.mockTiltak
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class SupplerendeStønadOver67 {
    @Test
    fun `SupplerendeStønadOver67 med gyldig periode skal validere ok`() {
        mockSpørsmålsbesvarelser(
            supplerendestønadover67 = mockSupplerendestønadover67(
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
    fun `SupplerendeStønadOver67 periode fra må være lik eller før fra dato`() {
        mockSpørsmålsbesvarelser(
            supplerendestønadover67 = mockSupplerendestønadover67(
                mottar = true,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 2),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
            mottarAndreUtbetalinger = true,
        ).valider(fixedClock) shouldContain "Perioden på SupplerendeStønadOver67 er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `SupplerendeStønadOver67 med mottar = false skal ikke ha en periode`() {
        mockSpørsmålsbesvarelser(
            supplerendestønadover67 = mockSupplerendestønadover67(
                mottar = false,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
            mottarAndreUtbetalinger = true,
        ).valider(fixedClock) shouldContain "SupplerendeStønadOver67 med mottar = false kan ikke ha noen periode"
    }

    @Test
    fun `SupplerendeStønadOver67 periode til kan ikke være tidligere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            supplerendestønadover67 = mockSupplerendestønadover67(
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
        ).valider(fixedClock) shouldContain "Perioden på SupplerendeStønadOver67 er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `SupplerendeStønadOver67 periode til kan ikke være senere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            supplerendestønadover67 = mockSupplerendestønadover67(
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
        ).valider(fixedClock) shouldContain "Perioden på SupplerendeStønadOver67 er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `SupplerendeStønadOver67 med mottar = true må ha en periode`() {
        mockSpørsmålsbesvarelser(
            supplerendestønadover67 = mockSupplerendestønadover67(
                mottar = true,
                periode = null,
            ),
            mottarAndreUtbetalinger = true,
        ).valider(fixedClock) shouldContain "SupplerendeStønadOver67 med mottar = true må ha periode"
    }
}
