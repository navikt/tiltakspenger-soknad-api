package no.nav.tiltakspenger.soknad.api.pdl.routes.dto

import java.time.LocalDate
import no.nav.tiltakspenger.libs.soknad.AdressebeskyttelseDTO as AdressebeskyttelseDtoLibs

data class BarnDTO(
    val fødselsdato: LocalDate,
    val fornavn: String? = null,
    val mellomnavn: String? = null,
    val etternavn: String? = null,
    val adressebeskyttelse: AdressebeskyttelseDTO,
)

data class PersonDTO(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val barn: List<BarnDTO> = emptyList(),
    val harFylt18År: Boolean?,
)

enum class AdressebeskyttelseDTO {
    STRENGT_FORTROLIG_UTLAND,
    STRENGT_FORTROLIG,
    FORTROLIG,
    UGRADERT,
    ;

    fun toAdresseBeskyttelseDTO(): AdressebeskyttelseDtoLibs = when (this) {
        STRENGT_FORTROLIG_UTLAND -> AdressebeskyttelseDtoLibs.STRENGT_FORTROLIG_UTLAND
        STRENGT_FORTROLIG -> AdressebeskyttelseDtoLibs.STRENGT_FORTROLIG
        FORTROLIG -> AdressebeskyttelseDtoLibs.FORTROLIG
        UGRADERT -> AdressebeskyttelseDtoLibs.UGRADERT
    }
}
