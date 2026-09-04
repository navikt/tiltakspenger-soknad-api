package no.nav.tiltakspenger.soknad.api.testutils

import java.time.LocalDate

// Wire-svarene de to kildene i tiltaksdeltakelse-hentingen gir, som ferdig JSON.
// Byggerne skriver JSON og ikke DTO-er med vilje: klientene i libs eier kontrakten, og deres egne DTO-typer er `internal`.
// Da kjører deserialiseringen for ekte i testene våre også, og et brudd i kontrakten slår ut her i stedet for først i drift.

/** Svaret PDLs `hentIdenter` gir, slik `PdlIdentklient` leser det. */
fun pdlIdenterRespons(vararg identer: String): String =
    """{"data": {"hentIdenter": {"identer": [${identer.joinToString(", ") { """{"ident": "$it"}""" }}]}}, "errors": null}"""

/** Et svar fra `tiltakshistorikk` med de radene testen sender inn. */
fun tiltakshistorikkRespons(vararg rader: String): String =
    """{"historikk": [${rader.joinToString(", ")}], "meldinger": []}"""

/**
 * Én deltakelse fra Komet, den vanligste kilden.
 * Standardverdiene gir en pågående deltakelse på et tiltak som gir rett, innenfor tidsrommet søknaden viser målt mot testklokka (2025-01-01).
 */
fun kometDeltakelse(
    fnr: String,
    id: String = "0190c9a2-2222-7000-8000-000000000002",
    gjennomføringId: String = "0190c9a2-3333-7000-8000-000000000003",
    tiltakskode: String = "ARBEIDSFORBEREDENDE_TRENING",
    tiltakstypenavn: String = "Arbeidsforberedende trening",
    tittel: String = "Arbeidsforberedende trening hos Testarrangør AS",
    arrangørnavn: String? = "Testarrangør AS",
    hovedenhetnavn: String? = null,
    status: String = "DELTAR",
    fraOgMed: LocalDate? = LocalDate.of(2024, 12, 1),
    tilOgMed: LocalDate? = LocalDate.of(2025, 2, 28),
): String = """
    {
      "type": "TeamKometDeltakelse",
      "norskIdent": "$fnr",
      "startDato": ${fraOgMed.somJsonDato()},
      "sluttDato": ${tilOgMed.somJsonDato()},
      "id": "$id",
      "tittel": "$tittel",
      "status": { "type": "$status", "aarsak": null, "opprettetDato": "2024-11-01T09:30:00" },
      "tiltakstype": { "tiltakskode": "$tiltakskode", "navn": "$tiltakstypenavn" },
      "gjennomforing": { "id": "$gjennomføringId", "navn": null, "deltidsprosent": null },
      "arrangor": {
        "hovedenhet": ${hovedenhetnavn.somVirksomhet()},
        "underenhet": { "navn": ${arrangørnavn.somJsonTekst()} }
      },
      "deltidsprosent": 60.0,
      "dagerPerUke": 3.0
    }
""".trimIndent()

/**
 * En deltakelsesform kontrakten ikke har i dag.
 * Den blir aldri en tiltaksdeltakelse, men bæres som en ukjent kildeverdi vi logger på.
 */
fun ukjentDeltakelsesform(type: String = "TeamNyDeltakelse"): String = """{"type": "$type"}"""

private fun LocalDate?.somJsonDato(): String = if (this == null) "null" else "\"$this\""

private fun String?.somJsonTekst(): String = if (this == null) "null" else "\"$this\""

/** Hovedenheten kan mangle helt, mens underenheten alltid er et objekt der bare navnet kan mangle. */
private fun String?.somVirksomhet(): String = if (this == null) "null" else """{ "navn": "$this" }"""
