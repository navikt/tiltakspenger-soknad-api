package no.nav.tiltakspenger.soknad.api.soknad

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.tiltakspenger.libs.json.objectMapper
import no.nav.tiltakspenger.soknad.api.domain.toSøknadDbJson
import no.nav.tiltakspenger.soknad.api.mockBarnetillegg
import no.nav.tiltakspenger.soknad.api.mockRegistrertBarn
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.mockTiltak
import no.nav.tiltakspenger.soknad.api.tiltak.Deltakelsesperiode
import org.junit.jupiter.api.Test
import tools.jackson.databind.node.ObjectNode
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
    fun `svaret på om søkeren søker barnetillegg overlever lagring som json`() {
        listOf(true, false).forEach { søkerOmBarnetillegg ->
            val besvarelser = mockSpørsmålsbesvarelser(
                barnetillegg = mockBarnetillegg(
                    søkerOmBarnetillegg = søkerOmBarnetillegg,
                    manueltRegistrerteBarnSøktBarnetilleggFor = emptyList(),
                    registrerteBarnSøktBarnetilleggFor = if (søkerOmBarnetillegg) listOf(mockRegistrertBarn()) else emptyList(),
                ),
            )

            besvarelser.toDbJson().toSpørsmålsbesvarelserDbJson().barnetillegg.søkerOmBarnetillegg shouldBe søkerOmBarnetillegg
        }
    }

    @Test
    fun `lagrede søknader fra før spørsmålet om barnetillegg fantes, får null som svar`() {
        val besvarelser = mockSpørsmålsbesvarelser()
        val json = objectMapper.readTree(besvarelser.toDbJson()) as ObjectNode
        (json["barnetillegg"] as ObjectNode).remove("søkerOmBarnetillegg")

        json.toString().toSpørsmålsbesvarelserDbJson().barnetillegg.søkerOmBarnetillegg shouldBe null
    }

    @Test
    fun `ugyldig json for søknad gir InvalidParameterException`() {
        shouldThrow<InvalidParameterException> { "ikke json".toSøknadDbJson() }
    }
}
