package no.nav.tiltakspenger.soknad.api.soknad.validering

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.mockAlderspensjon
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.mockTiltak
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class AlderspensjonTest {
    @Test
    fun `alderspensjon med gyldig fraDato skal validere ok`() {
        mockSpørsmålsbesvarelser(
            alderspensjon = mockAlderspensjon(
                mottar = true,
                fraDato = LocalDate.of(2025, 1, 1),
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
    fun `Alderspensjon med mottar = false skal ikke ha en fra dato`() {
        mockSpørsmålsbesvarelser(
            alderspensjon = mockAlderspensjon(
                mottar = false,
                fraDato = LocalDate.of(2025, 1, 1),
            ),
            mottarAndreUtbetalinger = true,
        ).valider(fixedClock) shouldContain "Alderspensjon med mottar = false kan ikke ha noen fra dato"
    }

    @Test
    fun `Alderspensjon fra dato kan ikke være senere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            alderspensjon = mockAlderspensjon(
                mottar = true,
                fraDato = LocalDate.of(2025, 1, 2),
            ),
            tiltak = mockTiltak(
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
            mottarAndreUtbetalinger = true,
        ).valider(fixedClock) shouldContain "Alderspensjon fra dato kan ikke være senere enn tiltakets periode"
    }

    @Test
    fun `Alderspensjon med mottar = true må ha en fra dato`() {
        mockSpørsmålsbesvarelser(
            alderspensjon = mockAlderspensjon(
                mottar = true,
                fraDato = null,
            ),
            mottarAndreUtbetalinger = true,
        ).valider(fixedClock) shouldContain "Alderspensjon med mottar = true må ha fra dato"
    }
}
