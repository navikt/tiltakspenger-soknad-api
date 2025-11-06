package no.nav.tiltakspenger.soknad.api.vedlegg

import no.nav.tiltakspenger.soknad.api.deserialize
import no.nav.tiltakspenger.soknad.api.serialize
import java.security.InvalidParameterException

data class Vedlegg(
    val filnavn: String,
    val contentType: String,
    val dokument: ByteArray,
    val brevkode: String = "S1",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Vedlegg

        if (filnavn != other.filnavn) return false
        if (contentType != other.contentType) return false
        if (!dokument.contentEquals(other.dokument)) return false
        if (brevkode != other.brevkode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filnavn.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + dokument.contentHashCode()
        result = 31 * result + brevkode.hashCode()
        return result
    }
}

fun String.vedleggDbJson(): List<Vedlegg> {
    try {
        return deserialize(this)
    } catch (exception: Exception) {
        throw InvalidParameterException("Det oppstod en feil ved parsing av json for vedlegg: " + exception.message)
    }
}

fun List<Vedlegg>.toDbJson(): String = serialize(this)
