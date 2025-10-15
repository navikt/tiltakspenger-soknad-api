package no.nav.tiltakspenger.soknad.api.pdl

data class Navn(
    val fornavn: String,
    val mellomnavn: String? = null,
    val etternavn: String,
)
