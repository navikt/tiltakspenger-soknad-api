package no.nav.tiltakspenger.soknad.api.soknad.validering

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.mockKvalifiseringsprogram
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.mockTiltak
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class KvalifiseringprogramTest {

    @Test
    fun `jobbsjansen med gyldig periode skal validere ok`() {
        mockSpørsmålsbesvarelser(
            kvalifiseringsprogram = mockKvalifiseringsprogram(
                deltar = true,
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
    fun `kvalifiseringsprogram periode fra må være lik eller før fra dato`() {
        mockSpørsmålsbesvarelser(
            kvalifiseringsprogram = mockKvalifiseringsprogram(
                deltar = true,
                periode = Periode(
                    fra = LocalDate.of(2025, 2, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
        ).valider(fixedClock) shouldContain "Perioden på KVP er ugyldig. Fra-dato må være tidligere enn, eller lik, til-dato."
    }

    @Test
    fun `kvalifiseringsprogram med deltar = false skal ikke ha en periode`() {
        mockSpørsmålsbesvarelser(
            kvalifiseringsprogram = mockKvalifiseringsprogram(
                deltar = false,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
        ).valider(fixedClock) shouldContain "Kvalifiseringsprogram med deltar = false kan ikke ha noen periode"
    }

    @Test
    fun `kvalifiseringsprogram periode til kan ikke være tidligere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            kvalifiseringsprogram = mockKvalifiseringsprogram(
                deltar = true,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 2),
                ),
            ),
            tiltak = mockTiltak(
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 2),
                    til = LocalDate.of(2025, 1, 2),
                ),
            ),
        ).valider(fixedClock) shouldContain "Perioden på KVP er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `kvalifiseringsprogram periode kan ikke være senere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            kvalifiseringsprogram = mockKvalifiseringsprogram(
                deltar = true,
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
        ).valider(fixedClock) shouldContain "Perioden på KVP er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `kvalifiseringsprogram med deltar = true må ha en periode`() {
        mockSpørsmålsbesvarelser(
            kvalifiseringsprogram = mockKvalifiseringsprogram(
                deltar = true,
                periode = null,
            ),
        ).valider(fixedClock) shouldContain "Kvalifisering med deltagelse må ha periode"
    }
}
