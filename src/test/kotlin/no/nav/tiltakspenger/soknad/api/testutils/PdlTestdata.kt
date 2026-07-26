package no.nav.tiltakspenger.soknad.api.testutils

import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering

/**
 * Rå GraphQL-svar fra PDL, slik de ser ut på tråden.
 * Vi bygger dem som JSON og ikke som DTO-er fordi det er formatet fra PDL vi vil at testene skal øve.
 */
private val metadata = """{ "endringer": [], "master": "FREG" }"""
private val folkeregistermetadata = """
    {
      "aarsak": null, "ajourholdstidspunkt": null, "gyldighetstidspunkt": null,
      "kilde": null, "opphoerstidspunkt": null, "sekvens": null
    }
""".trimIndent()

private fun navn(fornavn: String, mellomnavn: String?, etternavn: String) = """
    {
      "fornavn": "$fornavn",
      "mellomnavn": ${mellomnavn?.let { "\"$it\"" } ?: "null"},
      "etternavn": "$etternavn",
      "metadata": $metadata,
      "folkeregistermetadata": $folkeregistermetadata
    }
""".trimIndent()

private fun fødsel(dato: String) = """
    {
      "foedselsdato": "$dato",
      "metadata": $metadata,
      "folkeregistermetadata": $folkeregistermetadata
    }
""".trimIndent()

private fun adressebeskyttelse(gradering: AdressebeskyttelseGradering?) =
    gradering?.let { """{ "gradering": "$it", "metadata": $metadata, "folkeregistermetadata": $folkeregistermetadata }""" } ?: ""

private fun barnRelasjon(ident: String) = """
    {
      "relatertPersonsIdent": "$ident",
      "relatertPersonsRolle": "BARN",
      "metadata": $metadata,
      "folkeregistermetadata": $folkeregistermetadata
    }
""".trimIndent()

/** Svar på `hentPerson`-spørringen for søkeren selv. */
fun søkerRespons(
    fornavn: String = "Fornavn",
    mellomnavn: String? = null,
    etternavn: String = "Etternavn",
    fødselsdato: String = "1989-05-02",
    gradering: AdressebeskyttelseGradering? = null,
    barnIdenter: List<String> = emptyList(),
    død: Boolean = false,
): String = """
    {
      "data": {
        "hentPerson": {
          "navn": [ ${navn(fornavn, mellomnavn, etternavn)} ],
          "adressebeskyttelse": [ ${adressebeskyttelse(gradering)} ],
          "foedselsdato": [ ${fødsel(fødselsdato)} ],
          "forelderBarnRelasjon": [ ${barnIdenter.joinToString(",") { barnRelasjon(it) }} ],
          "doedsfall": [ ${if (død) """{ "doedsdato": "2024-01-01" }""" else ""} ]
        },
        "hentGeografiskTilknytning": {
          "gtType": "KOMMUNE", "gtKommune": "1122", "gtBydel": null, "gtLand": null
        }
      }
    }
""".trimIndent()

/** Svar på bolkoppslaget `hentPersonBolk` for søkerens barn. */
fun barnRespons(
    ident: String,
    fornavn: String = "Barn",
    etternavn: String = "Barnesen",
    fødselsdato: String = "2020-06-21",
): String = """
    {
      "data": {
        "hentPersonBolk": [ ${barnIBolk(ident, fornavn, etternavn, fødselsdato)} ]
      }
    }
""".trimIndent()

private fun barnIBolk(ident: String, fornavn: String, etternavn: String, fødselsdato: String) = """
    {
      "ident": "$ident",
      "code": "ok",
      "person": {
        "navn": [ ${navn(fornavn, null, etternavn)} ],
        "adressebeskyttelse": [],
        "foedselsdato": [ ${fødsel(fødselsdato)} ],
        "doedsfall": []
      }
    }
""".trimIndent()
