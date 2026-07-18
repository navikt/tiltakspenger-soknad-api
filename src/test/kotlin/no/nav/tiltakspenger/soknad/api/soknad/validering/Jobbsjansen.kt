package no.nav.tiltakspenger.soknad.api.soknad.validering

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.mockJobbsjansen
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.mockTiltak
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class Jobbsjansen {
    @Test
    fun `jobbsjansen med gyldig periode skal validere ok`() {
        mockSpørsmålsbesvarelser(
            mottarAndreUtbetalinger = true,
            jobbsjansen = mockJobbsjansen(
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
    fun `Jobbsjansen periode fra må være lik eller før fra dato`() {
        mockSpørsmålsbesvarelser(
            mottarAndreUtbetalinger = true,
            jobbsjansen = mockJobbsjansen(
                mottar = true,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 2),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
        ).valider(fixedClock) shouldContain "Perioden på Jobbsjansen er ugyldig. Fra-dato må være tidligere enn, eller lik, til-dato."
    }

    @Test
    fun `Jobbsjansen med mottar = false skal ikke ha en periode`() {
        mockSpørsmålsbesvarelser(
            mottarAndreUtbetalinger = true,
            jobbsjansen = mockJobbsjansen(
                mottar = false,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
        ).valider(fixedClock) shouldContain "Jobbsjansen med mottar = false kan ikke ha noen periode"
    }

    @Test
    fun `Jobbsjansen periode til kan ikke være tidligere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            mottarAndreUtbetalinger = true,
            jobbsjansen = mockJobbsjansen(
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
        ).valider(fixedClock) shouldContain "Perioden på Jobbsjansen er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `Jobbsjansen periode kan ikke være senere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            mottarAndreUtbetalinger = true,
            jobbsjansen = mockJobbsjansen(
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
        ).valider(fixedClock) shouldContain "Perioden på Jobbsjansen er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `Jobbsjansen med mottar = true må ha en periode`() {
        mockSpørsmålsbesvarelser(
            mottarAndreUtbetalinger = true,
            jobbsjansen = mockJobbsjansen(
                mottar = true,
                periode = null,
            ),
        ).valider(fixedClock) shouldContain "Jobbsjansen med mottar = true må ha periode"
    }
}
