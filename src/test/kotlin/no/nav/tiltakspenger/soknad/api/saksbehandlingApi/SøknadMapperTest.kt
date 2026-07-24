package no.nav.tiltakspenger.soknad.api.saksbehandlingApi

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.json.objectMapper
import no.nav.tiltakspenger.libs.soknad.SpmSvarDTO
import no.nav.tiltakspenger.soknad.api.soknad.validering.spørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.soknad.validering.søknad
import org.junit.jupiter.api.Test

internal class SøknadMapperTest {
    @Test
    fun `mapper og serialiserer journalpostId som streng`() {
        val dto = søknadMapper(
            søknad = søknad(),
            jounalpostId = JournalpostId("123456789"),
            saksnummer = "SAK-1",
        )

        val json = objectMapper.writeValueAsString(dto)

        dto.journalpostId shouldBe "123456789"
        json shouldContain "\"journalpostId\":\"123456789\""
    }

    @Test
    fun `nullstiller andre utbetalinger og mapper Nei-svar når bruker ikke mottar andre utbetalinger`() {
        val dto = søknadMapper(
            søknad = søknad(spørsmålsbesvarelser = spørsmålsbesvarelser(mottarAndreUtbetalinger = false)),
            jounalpostId = JournalpostId("123456789"),
            saksnummer = "SAK-1",
        )

        dto.gjenlevendepensjon.svar shouldBe SpmSvarDTO.Nei
        dto.gjenlevendepensjon.fom shouldBe null
        dto.gjenlevendepensjon.tom shouldBe null
        dto.alderspensjon.svar shouldBe SpmSvarDTO.Nei
        dto.alderspensjon.fom shouldBe null
        dto.supplerendeStønadFlyktning.svar shouldBe SpmSvarDTO.Nei
        dto.supplerendeStønadAlder.svar shouldBe SpmSvarDTO.Nei
        dto.jobbsjansen.svar shouldBe SpmSvarDTO.Nei
        dto.trygdOgPensjon.svar shouldBe SpmSvarDTO.Nei
    }
}
