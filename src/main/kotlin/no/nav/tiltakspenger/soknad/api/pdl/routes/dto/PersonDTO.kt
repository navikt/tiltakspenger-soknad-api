package no.nav.tiltakspenger.soknad.api.pdl.routes.dto

import java.time.LocalDate

data class BarnDTO(
    val fnr: String,
    val fødselsdato: LocalDate,
    val fornavn: String? = null,
    val mellomnavn: String? = null,
    val etternavn: String? = null,
)

data class PersonDTO(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val harFylt18År: Boolean?,
)

data class BarnListeDTO(
    val barn: List<BarnDTO>,
)
