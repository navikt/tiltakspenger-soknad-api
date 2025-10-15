package no.nav.tiltakspenger.soknad.api.pdl.client.dto

import no.nav.tiltakspenger.soknad.api.pdl.Adressebeskyttelse
import no.nav.tiltakspenger.soknad.api.pdl.Person
import no.nav.tiltakspenger.soknad.api.pdl.avklarGradering

data class SøkersBarnFraPDL(
    val navn: List<Navn>,
    val adressebeskyttelse: List<Adressebeskyttelse>,
    val foedselsdato: List<Fødsel>,
    val doedsfall: List<Dødsfall>,
)

data class SøkersBarnFraPDLRespons(
    val hentPerson: SøkersBarnFraPDL?,
)

data class SøkersBarnRespons(
    val data: SøkersBarnFraPDLRespons? = null,
    val errors: List<PdlError> = emptyList(),
) {
    private fun extractPerson(): SøkersBarnFraPDL? {
        if (this.errors.isNotEmpty()) {
            throw IllegalStateException(this.errors.firstOrNull()?.toString())
        }
        return this.data?.hentPerson
    }

    fun toPerson(): Person {
        val person = extractPerson() ?: throw IllegalStateException("Fant ikke personen")
        val navn = avklarNavn(person.navn)
        val fødsel = avklarFødsel(person.foedselsdato)
        val dødsfall = person.doedsfall.isNotEmpty()
        val adressebeskyttelseGradering = avklarGradering(person.adressebeskyttelse)
        return Person(
            fornavn = navn.fornavn,
            mellomnavn = navn.mellomnavn,
            etternavn = navn.etternavn,
            adressebeskyttelseGradering = adressebeskyttelseGradering,
            fødselsdato = fødsel.foedselsdato,
            erDød = dødsfall,
        )
    }
}
