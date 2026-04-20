package no.nav.tiltakspenger.soknad.api.saksbehandlingApi

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.json.objectMapper
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
}
