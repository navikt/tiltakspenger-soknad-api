package no.nav.tiltakspenger.soknad.api.db

import io.kotest.matchers.shouldBe
import kotliquery.sessionOf
import org.junit.jupiter.api.Test

class DatabaseExtTest {
    @Test
    fun `hent, hentListe og booleanOrNull mapper rader fra databasen`() {
        testDatabaseManager.withMigratedDb { dataSource ->
            sessionOf(dataSource).use { session ->
                "select :verdi as verdi".hent(mapOf("verdi" to 42), session) { row -> row.int("verdi") } shouldBe 42

                "select verdi from (values (1), (2)) as t(verdi) order by verdi"
                    .hentListe(session = session) { row -> row.int("verdi") } shouldBe listOf(1, 2)

                "select true as verdi".hent(session = session) { row -> row.booleanOrNull("verdi") } shouldBe true
                "select null::boolean as verdi".hent(session = session) { row -> row.booleanOrNull("verdi") } shouldBe null
            }
        }
    }
}
