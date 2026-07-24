package no.nav.tiltakspenger.soknad.api.soknad

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.tiltakspenger.soknad.api.domain.toSøknadDbJson
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.mockTiltak
import no.nav.tiltakspenger.soknad.api.tiltak.Deltakelsesperiode
import org.junit.jupiter.api.Test
import java.security.InvalidParameterException
import java.time.LocalDate

class SpørsmålsbesvarelserDTOTest {
    @Test
    fun `søkt periode valideres mot arena-perioden når arena kun har fradato`() {
        val kunFradatoIArena = Deltakelsesperiode(fra = LocalDate.of(2025, 1, 1), til = null)

        mockTiltak(
            periode = Periode(fra = LocalDate.of(2025, 1, 2), til = LocalDate.of(2025, 1, 3)),
            arenaRegistrertPeriode = kunFradatoIArena,
        ).søktPeriodeErInnenforArenaRegistrertPeriode() shouldBe true

        mockTiltak(
            periode = Periode(fra = LocalDate.of(2024, 12, 30), til = LocalDate.of(2025, 1, 3)),
            arenaRegistrertPeriode = kunFradatoIArena,
        ).søktPeriodeErInnenforArenaRegistrertPeriode() shouldBe false
    }

    @Test
    fun `like tiltak har lik hashCode, ulike har ulik`() {
        val tiltak = mockTiltak(gjennomforingId = "abc")

        tiltak.hashCode() shouldBe mockTiltak(gjennomforingId = "abc").hashCode()
        tiltak.hashCode() shouldNotBe mockTiltak(gjennomforingId = "def", aktivitetId = "456").hashCode()
    }

    @Test
    fun `ugyldig json for spørsmålsbesvarelser gir InvalidParameterException`() {
        shouldThrow<InvalidParameterException> { "ikke json".toSpørsmålsbesvarelserDbJson() }
    }

    @Test
    fun `gyldig json for spørsmålsbesvarelser deserialiseres`() {
        val besvarelser = mockSpørsmålsbesvarelser()

        besvarelser.toDbJson().toSpørsmålsbesvarelserDbJson() shouldBe besvarelser
    }

    @Test
    fun `ugyldig json for søknad gir InvalidParameterException`() {
        shouldThrow<InvalidParameterException> { "ikke json".toSøknadDbJson() }
    }
}
