package no.nav.tiltakspenger.soknad.api.pdl

import no.nav.tiltakspenger.libs.common.personopplysning.Fnr
import no.nav.tiltakspenger.soknad.api.isSameOrBefore
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.ForelderBarnRelasjon
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.ForelderBarnRelasjonRolle
import no.nav.tiltakspenger.soknad.api.pdl.routes.dto.BarnDTO
import no.nav.tiltakspenger.soknad.api.pdl.routes.dto.PersonDTO
import java.time.LocalDate

data class Person(
    val fnr: Fnr,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val fødselsdato: LocalDate? = null,
    val erDød: Boolean,
    val forelderBarnRelasjon: List<ForelderBarnRelasjon>? = emptyList(),
    val adressebeskyttelseGradering: AdressebeskyttelseGradering,
    val geografiskTilknytning: String?,
) {
    fun toPersonDTO(dagensDato: LocalDate): PersonDTO = PersonDTO(
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        etternavn = etternavn,
        harFylt18År = fødselsdato?.isSameOrBefore(dagensDato.minusYears(18))
            ?: throw IllegalStateException("Søker mangler fødselsdato"),
    )

    /** Barn med adressebeskyttelse får ikke med navnet. */
    fun toBarnDTO(): BarnDTO =
        if (adressebeskyttelseGradering === AdressebeskyttelseGradering.UGRADERT) {
            BarnDTO(
                fnr = fnr.verdi,
                fødselsdato = fødselsdato!!,
                fornavn = fornavn,
                mellomnavn = mellomnavn,
                etternavn = etternavn,
            )
        } else {
            BarnDTO(fnr = fnr.verdi, fødselsdato = fødselsdato!!)
        }

    fun barnsIdenter(): List<String> {
        return (forelderBarnRelasjon ?: emptyList())
            .filter { it.relatertPersonsRolle == ForelderBarnRelasjonRolle.BARN }
            .mapNotNull { it.relatertPersonsIdent }
            .distinct()
    }

    fun erUnder16ÅrPåDato(dato: LocalDate): Boolean {
        val datoMinus16år = dato.minusYears(16)
        return this.fødselsdato?.let {
            return it.isAfter(datoMinus16år)
        } ?: throw IllegalStateException("Barn mangler fødselsdato")
    }

    fun getNavn() = Navn(
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        etternavn = etternavn,
    )
}
