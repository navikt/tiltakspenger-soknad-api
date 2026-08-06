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

/**
 * PDL svarer `folkeregistermetadata: null` på opplysninger som ikke er mastret i Folkeregisteret.
 * Feltet er nullable i PDL-skjemaet, og fixturene må kunne gjengi begge formene.
 */
private fun fregmeta(uten: Boolean) = if (uten) "null" else folkeregistermetadata

private fun navn(fornavn: String, mellomnavn: String?, etternavn: String, utenFregmeta: Boolean) = """
    {
      "fornavn": "$fornavn",
      "mellomnavn": ${mellomnavn?.let { "\"$it\"" } ?: "null"},
      "etternavn": "$etternavn",
      "metadata": $metadata,
      "folkeregistermetadata": ${fregmeta(utenFregmeta)}
    }
""".trimIndent()

private fun fødsel(dato: String, utenFregmeta: Boolean) = """
    {
      "foedselsdato": "$dato",
      "metadata": $metadata,
      "folkeregistermetadata": ${fregmeta(utenFregmeta)}
    }
""".trimIndent()

private fun adressebeskyttelse(gradering: AdressebeskyttelseGradering?, utenFregmeta: Boolean) =
    gradering?.let { """{ "gradering": "$it", "metadata": $metadata, "folkeregistermetadata": ${fregmeta(utenFregmeta)} }""" } ?: ""

private fun barnRelasjon(ident: String, utenFregmeta: Boolean) = """
    {
      "relatertPersonsIdent": "$ident",
      "relatertPersonsRolle": "BARN",
      "metadata": $metadata,
      "folkeregistermetadata": ${fregmeta(utenFregmeta)}
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
    utenFregmeta: Boolean = false,
): String = """
    {
      "data": {
        "hentPerson": {
          "navn": [ ${navn(fornavn, mellomnavn, etternavn, utenFregmeta)} ],
          "adressebeskyttelse": [ ${adressebeskyttelse(gradering, utenFregmeta)} ],
          "foedselsdato": [ ${fødsel(fødselsdato, utenFregmeta)} ],
          "forelderBarnRelasjon": [ ${barnIdenter.joinToString(",") { barnRelasjon(it, utenFregmeta) }} ],
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
    utenFregmeta: Boolean = false,
): String = """
    {
      "data": {
        "hentPersonBolk": [ ${barnIBolk(ident, fornavn, etternavn, fødselsdato, utenFregmeta)} ]
      }
    }
""".trimIndent()

private fun barnIBolk(ident: String, fornavn: String, etternavn: String, fødselsdato: String, utenFregmeta: Boolean) = """
    {
      "ident": "$ident",
      "code": "ok",
      "person": {
        "navn": [ ${navn(fornavn, null, etternavn, utenFregmeta)} ],
        "adressebeskyttelse": [],
        "foedselsdato": [ ${fødsel(fødselsdato, utenFregmeta)} ],
        "doedsfall": []
      }
    }
""".trimIndent()
