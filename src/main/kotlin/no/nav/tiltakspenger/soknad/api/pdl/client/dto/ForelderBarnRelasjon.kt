package no.nav.tiltakspenger.soknad.api.pdl.client.dto

enum class ForelderBarnRelasjonRolle {
    BARN,
    FAR,
    MOR,
    MEDMOR,
}

data class ForelderBarnRelasjon(
    val relatertPersonsIdent: String?,
    val relatertPersonsRolle: ForelderBarnRelasjonRolle,
    override val folkeregistermetadata: FolkeregisterMetadata?,
    override val metadata: EndringsMetadata,
) : Changeable
