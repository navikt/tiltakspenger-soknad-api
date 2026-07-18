package no.nav.tiltakspenger.soknad.api.soknad.validering

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.mockInstitusjonsopphold
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.mockTiltak
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class InstitusjonsoppholdTest {

    @Test
    fun `institusjonsopphold med gyldig periode skal validere ok`() {
        mockSpørsmålsbesvarelser(
            institusjonsopphold = mockInstitusjonsopphold(
                borPåInstitusjon = true,
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
    fun `institusjonsopphold periode fra må være lik eller før fra dato`() {
        mockSpørsmålsbesvarelser(
            institusjonsopphold = mockInstitusjonsopphold(
                borPåInstitusjon = true,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 2),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
        ).valider(fixedClock) shouldContain "Perioden på institusjonsopphold er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `institusjonsopphold med borPåInstitusjon = false skal ikke ha en periode`() {
        mockSpørsmålsbesvarelser(
            institusjonsopphold = mockInstitusjonsopphold(
                borPåInstitusjon = false,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
        ).valider(fixedClock) shouldContain "Institusjonsopphold med borPåInstitusjon = false kan ikke ha noen periode"
    }

    @Test
    fun `institusjonsopphold periode til kan ikke være tidligere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            institusjonsopphold = mockInstitusjonsopphold(
                borPåInstitusjon = true,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
            tiltak = mockTiltak(
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 2),
                    til = LocalDate.of(2025, 1, 2),
                ),
            ),
        ).valider(fixedClock) shouldContain "Perioden på institusjonsopphold er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `institusjonsopphold periode kan ikke være senere enn tiltakets periode`() {
        mockSpørsmålsbesvarelser(
            institusjonsopphold = mockInstitusjonsopphold(
                borPåInstitusjon = true,
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 2),
                    til = LocalDate.of(2025, 1, 2),
                ),
            ),
            tiltak = mockTiltak(
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
        ).valider(fixedClock) shouldContain "Perioden på institusjonsopphold er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket."
    }

    @Test
    fun `institusjonsopphold med borPåInstitusjon = true må ha en periode`() {
        mockSpørsmålsbesvarelser(
            institusjonsopphold = mockInstitusjonsopphold(
                borPåInstitusjon = true,
                periode = null,
            ),
        ).valider(fixedClock) shouldContain "Institusjonsopphold med deltagelse må ha periode"
    }
}
