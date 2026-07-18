package no.nav.tiltakspenger.soknad.api.soknad.validering

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.mockTiltak
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import no.nav.tiltakspenger.soknad.api.tiltak.Deltakelsesperiode
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class TitakTest {
    @Test
    fun `hvis tiltaksperioden er gyldig og innenfor arenaRegistrertPeriode, skal tiltaket i søknaden validere ok`() {
        mockSpørsmålsbesvarelser(
            tiltak = mockTiltak(
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
                arenaRegistrertPeriode = Deltakelsesperiode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
        ).valider(fixedClock) shouldBe emptyList()
    }

    @Test
    fun `tiltaksperioden kan ikke være helt utenfor perioden som er registrert på tiltaket i Arena`() {
        mockSpørsmålsbesvarelser(
            tiltak = mockTiltak(
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
                arenaRegistrertPeriode = Deltakelsesperiode(
                    fra = LocalDate.of(2025, 1, 2),
                    til = LocalDate.of(2025, 1, 2),
                ),
            ),
        ).valider(fixedClock) shouldContain "Bruker kan ikke søke utenfor den registrerte tiltaksperioden"
    }

    @Test
    fun `tiltaksperioden kan ikke starte tidligere enn perioden som er registrert på tiltaket i Arena`() {
        mockSpørsmålsbesvarelser(
            tiltak = mockTiltak(
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 2),
                ),
                arenaRegistrertPeriode = Deltakelsesperiode(
                    fra = LocalDate.of(2025, 1, 2),
                    til = LocalDate.of(2025, 1, 2),
                ),
            ),
        ).valider(fixedClock) shouldContain "Bruker kan ikke søke utenfor den registrerte tiltaksperioden"
    }

    @Test
    fun `tiltaksperioden kan ikke slutte senere enn perioden som er registrert på tiltaket i Arena`() {
        mockSpørsmålsbesvarelser(
            tiltak = mockTiltak(
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 2),
                ),
                arenaRegistrertPeriode = Deltakelsesperiode(
                    fra = LocalDate.of(2025, 1, 1),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
        ).valider(fixedClock) shouldContain "Bruker kan ikke søke utenfor den registrerte tiltaksperioden"
    }

    @Test
    fun `tiltaksperioden må starte før den slutter`() {
        mockSpørsmålsbesvarelser(
            tiltak = mockTiltak(
                periode = Periode(
                    fra = LocalDate.of(2025, 1, 2),
                    til = LocalDate.of(2025, 1, 1),
                ),
            ),
        ).valider(fixedClock) shouldContain "Perioden på tiltaket er ugyldig. Fra-dato må være tidligere enn, eller lik, til-dato."
    }
}
