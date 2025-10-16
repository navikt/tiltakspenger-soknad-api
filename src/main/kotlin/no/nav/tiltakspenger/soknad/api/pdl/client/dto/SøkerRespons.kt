package no.nav.tiltakspenger.soknad.api.pdl.client.dto

import io.ktor.util.toUpperCasePreservingASCIIRules
import no.nav.tiltakspenger.soknad.api.pdl.Adressebeskyttelse
import no.nav.tiltakspenger.soknad.api.pdl.Person
import no.nav.tiltakspenger.soknad.api.pdl.avklarGradering

data class SøkerFraPDL(
    val navn: List<Navn>,
    val adressebeskyttelse: List<Adressebeskyttelse>,
    val foedselsdato: List<Fødsel>,
    val forelderBarnRelasjon: List<ForelderBarnRelasjon>,
    val doedsfall: List<Dødsfall>,
)

data class GeografiskTilknytning(
    val gtType: String,
    val gtKommune: String?,
    val gtBydel: String?,
    val gtLand: String?,
) {
    fun getGT(): String? =
        when (gtType.toUpperCasePreservingASCIIRules()) {
            "KOMMUNE" -> gtKommune
            "BYDEL" -> gtBydel
            "UTLAND" -> gtLand
            "UDEFINERT" -> gtType
            else -> null
        }
}

data class SøkerRespons(
    val hentPerson: SøkerFraPDL?,
    val hentGeografiskTilknytning: GeografiskTilknytning?,
) {
    fun toPerson(): Person {
        val person = hentPerson ?: throw IllegalStateException("Fant ikke personen")
        val navn = avklarNavn(person.navn)
        val fødsel = avklarFødsel(person.foedselsdato)
        if (person.doedsfall.isNotEmpty()) {
            throw IllegalStateException("Søker er registrert som død i PDL")
        }
        val adressebeskyttelseGradering = avklarGradering(person.adressebeskyttelse)
        return Person(
            fornavn = navn.fornavn,
            mellomnavn = navn.mellomnavn,
            etternavn = navn.etternavn,
            fødselsdato = fødsel.foedselsdato,
            forelderBarnRelasjon = person.forelderBarnRelasjon,
            adressebeskyttelseGradering = adressebeskyttelseGradering,
            erDød = false,
            geografiskTilknytning = hentGeografiskTilknytning?.getGT(),
        )
    }
}
