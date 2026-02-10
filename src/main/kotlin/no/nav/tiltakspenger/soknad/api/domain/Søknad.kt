package no.nav.tiltakspenger.soknad.api.domain

import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.libs.json.serialize
import no.nav.tiltakspenger.soknad.api.soknad.SpørsmålsbesvarelserDTO
import java.security.InvalidParameterException
import java.time.LocalDateTime

data class Personopplysninger(
    val ident: String,
    val fornavn: String,
    val etternavn: String,
)

/**
 * Søknaden rett før vi journalfører den.
 * TODO post-mvp jah: Denne tilfører ikke noe nytt over MottattSøknad, bør vi fjerne denne? Eventuelt endre den til å være en JournalførtSøknad?
 */
data class Søknad(
    val id: String,
    val acr: String,
    val versjon: String,
    val spørsmålsbesvarelser: SpørsmålsbesvarelserDTO,
    val vedleggsnavn: List<String>,
    val personopplysninger: Personopplysninger,
    val innsendingTidspunkt: LocalDateTime,
) {
    companion object {
        fun toSøknad(
            id: String,
            acr: String,
            spørsmålsbesvarelser: SpørsmålsbesvarelserDTO,
            vedleggsnavn: List<String>,
            fnr: String,
            fornavn: String,
            etternavn: String,
            innsendingTidspunkt: LocalDateTime,
        ): Søknad {
            return Søknad(
                id = id,
                acr = acr,
                versjon = "4",
                spørsmålsbesvarelser = spørsmålsbesvarelser,
                vedleggsnavn = vedleggsnavn,
                personopplysninger = Personopplysninger(
                    ident = fnr,
                    fornavn = fornavn,
                    etternavn = etternavn,
                ),
                innsendingTidspunkt = innsendingTidspunkt,
            )
        }
    }
}

fun String.toSøknadDbJson(): Søknad {
    try {
        return deserialize(this)
    } catch (exception: Exception) {
        throw InvalidParameterException("Det oppstod en feil ved parsing av json for søknad: " + exception.message)
    }
}

fun Søknad.toDbJson(): String = serialize(this)
