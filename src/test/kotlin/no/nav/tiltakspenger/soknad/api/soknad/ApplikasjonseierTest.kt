package no.nav.tiltakspenger.soknad.api.soknad

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ApplikasjonseierTest {
    @Test
    fun `toDb og toApplikasjonseier er hverandres inverser`() {
        Applikasjonseier.Arena.toDb() shouldBe "arena"
        Applikasjonseier.Tiltakspenger.toDb() shouldBe "tp"
        Applikasjonseier.toApplikasjonseier("arena") shouldBe Applikasjonseier.Arena
        Applikasjonseier.toApplikasjonseier("tp") shouldBe Applikasjonseier.Tiltakspenger
    }

    @Test
    fun `ukjent eier i databasen kaster`() {
        shouldThrow<IllegalStateException> { Applikasjonseier.toApplikasjonseier("ukjent") }
    }
}
