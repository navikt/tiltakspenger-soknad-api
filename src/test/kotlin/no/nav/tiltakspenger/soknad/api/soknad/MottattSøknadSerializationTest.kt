package no.nav.tiltakspenger.soknad.api.soknad

import io.kotest.matchers.string.shouldContain
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.json.objectMapper
import no.nav.tiltakspenger.soknad.api.util.genererMottattSøknadForTest
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class MottattSøknadSerializationTest {
    @Test
    fun `serialiserer journalpostId som streng`() {
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = LocalDateTime.now(),
            eier = Applikasjonseier.Tiltakspenger,
        ).copy(
            journalført = LocalDateTime.now(),
            journalpostId = JournalpostId("123456789"),
        )

        val json = objectMapper.writeValueAsString(mottattSøknad)

        json shouldContain "\"journalpostId\":\"123456789\""
    }
}
