package no.nav.tiltakspenger.soknad.api.saksbehandlingApi

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.libs.json.serialize
import org.junit.jupiter.api.Test

class SaksbehandlingApiDtoTest {
    @Test
    fun `FnrDTO serialiseres med fnr som felt`() {
        serialize(FnrDTO(fnr = "12345678910")) shouldBe """{"fnr":"12345678910"}"""
    }

    @Test
    fun `SaksnummerResponse deserialiseres fra json`() {
        deserialize<SaksnummerResponse>("""{"saksnummer":"SAK-1"}""") shouldBe SaksnummerResponse(saksnummer = "SAK-1")
    }
}
