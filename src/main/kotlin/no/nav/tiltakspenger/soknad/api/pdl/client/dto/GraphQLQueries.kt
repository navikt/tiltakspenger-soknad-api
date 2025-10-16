package no.nav.tiltakspenger.soknad.api.pdl.client.dto

import no.nav.tiltakspenger.soknad.api.pdl.client.PdlClient

val hentPersonQueryString = requireNotNull(PdlClient::class.java.getResource("/hentPersonQuery.graphql")).readText()
val hentBarnQueryString = requireNotNull(PdlClient::class.java.getResource("/hentBarnQuery.graphql")).readText()

fun hentPersonQuery(ident: String): GraphqlQuery {
    return GraphqlQuery(
        query = hentPersonQueryString,
        variables = mapOf(
            "ident" to ident,
        ),
    )
}

fun hentBarnQuery(ident: String): GraphqlQuery {
    return GraphqlQuery(
        query = hentBarnQueryString,
        variables = mapOf(
            "ident" to ident,
        ),
    )
}

data class GraphqlQuery(
    val query: String,
    val variables: Map<String, String>,
)
