package no.nav.tiltakspenger.soknad.api.pdl.client.dto

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.tiltakspenger.soknad.api.testutils.nyttTestFødselsnummer
import org.junit.jupiter.api.Test

class GraphQLQueriesTest {
    @Test
    fun `hentPersonQuery bygger query med ident som variabel`() {
        val ident = nyttTestFødselsnummer()
        val query = hentPersonQuery(ident)

        query.query shouldContain "hentPerson"
        query.variables shouldBe mapOf("ident" to ident)
    }

    @Test
    fun `hentBarnBolkQuery bygger bolk-query med identene som variabel`() {
        val identer = listOf(nyttTestFødselsnummer(), nyttTestFødselsnummer())
        val query = hentBarnBolkQuery(identer)

        query.query shouldContain "hentPersonBolk"
        query.variables shouldBe mapOf("identer" to identer)
    }
}
