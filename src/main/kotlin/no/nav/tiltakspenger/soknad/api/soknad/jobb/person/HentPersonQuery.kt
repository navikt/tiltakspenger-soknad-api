package no.nav.tiltakspenger.soknad.api.soknad.jobb.person

import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.personklient.pdl.GraphqlQuery

fun hentPersonQuery(fnr: Fnr): GraphqlQuery {
    return GraphqlQuery(
        query = query,
        variables = mapOf(
            "ident" to fnr.verdi,
        ),
    )
}

private val query = """
query(${'$'}ident: ID!){
    hentPerson(ident: ${'$'}ident) {
        adressebeskyttelse(historikk: false) {
            gradering
            folkeregistermetadata {
                ...folkeregistermetadataDetails
            }
            metadata {
                ...metadataDetails
            }
        }
        navn(historikk: false) {
            fornavn
            mellomnavn
            etternavn
            folkeregistermetadata {
                ...folkeregistermetadataDetails
            }
            metadata {
                ...metadataDetails
            }
        }
    },
    hentGeografiskTilknytning(ident: ${'$'}ident) {
        gtKommune
        gtBydel
        gtLand
        gtType
    }
}

fragment folkeregistermetadataDetails on Folkeregistermetadata {
    aarsak
    ajourholdstidspunkt
    gyldighetstidspunkt
    kilde
    opphoerstidspunkt
    sekvens
}

fragment metadataDetails on Metadata {
    endringer {
        kilde
        registrert
        registrertAv
        systemkilde
        type
    }
    master
}
""".trimIndent()
