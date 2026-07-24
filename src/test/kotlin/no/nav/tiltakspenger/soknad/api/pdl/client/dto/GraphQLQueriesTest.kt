package no.nav.tiltakspenger.soknad.api.pdl.client.dto

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class GraphQLQueriesTest {
    @Test
    fun `hentPersonQuery bygger query med ident som variabel`() {
        val query = hentPersonQuery("12345678910")

        query.query shouldContain "hentPerson"
        query.variables shouldBe mapOf("ident" to "12345678910")
    }

    @Test
    fun `hentBarnBolkQuery bygger bolk-query med identene som variabel`() {
        val identer = listOf("11111111111", "22222222222")
        val query = hentBarnBolkQuery(identer)

        query.query shouldContain "hentPersonBolk"
        query.variables shouldBe mapOf("identer" to identer)
    }
}
