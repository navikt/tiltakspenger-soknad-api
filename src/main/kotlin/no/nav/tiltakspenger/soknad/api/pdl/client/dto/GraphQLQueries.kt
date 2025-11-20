package no.nav.tiltakspenger.soknad.api.pdl.client.dto

import no.nav.tiltakspenger.libs.personklient.pdl.GraphqlBolkQuery
import no.nav.tiltakspenger.soknad.api.pdl.client.PdlClient

val hentPersonQueryString = requireNotNull(PdlClient::class.java.getResource("/hentPersonQuery.graphql")).readText()
val hentBarnBolkQueryString = requireNotNull(PdlClient::class.java.getResource("/hentBarnBolkQuery.graphql")).readText()

fun hentPersonQuery(ident: String): GraphqlQuery {
    return GraphqlQuery(
        query = hentPersonQueryString,
        variables = mapOf(
            "ident" to ident,
        ),
    )
}

fun hentBarnBolkQuery(identer: List<String>): GraphqlBolkQuery {
    return GraphqlBolkQuery(
        query = hentBarnBolkQueryString,
        variables = mapOf(
            "identer" to identer,
        ),
    )
}

data class GraphqlQuery(
    val query: String,
    val variables: Map<String, String>,
)
