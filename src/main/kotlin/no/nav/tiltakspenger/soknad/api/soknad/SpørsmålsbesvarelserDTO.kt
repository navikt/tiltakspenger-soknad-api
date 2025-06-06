package no.nav.tiltakspenger.soknad.api.soknad

import no.nav.tiltakspenger.libs.common.SaniterStringForPdfgen.saniter
import no.nav.tiltakspenger.soknad.api.deserialize
import no.nav.tiltakspenger.soknad.api.isSameOrAfter
import no.nav.tiltakspenger.soknad.api.isSameOrBefore
import no.nav.tiltakspenger.soknad.api.serialize
import no.nav.tiltakspenger.soknad.api.tiltak.Deltakelsesperiode
import java.security.InvalidParameterException
import java.time.LocalDate

data class Periode(
    val fra: LocalDate,
    val til: LocalDate,
) {
    fun erGyldig(): Boolean {
        return fra.isSameOrBefore(til)
    }

    fun erInnenfor(periode: Periode): Boolean {
        return fra.isSameOrAfter(periode.fra) &&
            fra.isSameOrBefore(periode.til) &&
            til.isSameOrAfter(periode.fra) &&
            til.isSameOrBefore(periode.til)
    }
}

class ManueltRegistrertBarn(
    fornavn: String,
    mellomnavn: String?,
    etternavn: String,
    fødselsdato: LocalDate,
    oppholdInnenforEøs: Boolean,
) {
    val fornavn: String = saniter(fornavn)
    val mellomnavn: String? = mellomnavn?.let { saniter(mellomnavn) }
    val etternavn: String = saniter(etternavn)
    val fødselsdato: LocalDate = fødselsdato
    val oppholdInnenforEøs = oppholdInnenforEøs
}

class RegistrertBarn(
    fornavn: String?,
    mellomnavn: String?,
    etternavn: String?,
    fødselsdato: LocalDate,
    oppholdInnenforEøs: Boolean,
) {
    val fornavn: String? = fornavn?.let { saniter(fornavn) }
    val mellomnavn: String? = mellomnavn?.let { saniter(mellomnavn) }
    val etternavn: String? = etternavn?.let { saniter(etternavn) }
    val fødselsdato: LocalDate = fødselsdato
    val oppholdInnenforEøs = oppholdInnenforEøs
}

data class Kvalifiseringsprogram(
    val deltar: Boolean,
    val periode: Periode?,
)

data class Introduksjonsprogram(
    val deltar: Boolean,
    val periode: Periode?,
)

data class Institusjonsopphold(
    val borPåInstitusjon: Boolean,
    val periode: Periode?,
)

class Tiltak(
    aktivitetId: String,
    periode: Periode,
    arenaRegistrertPeriode: Deltakelsesperiode?,
    arrangør: String,
    type: String,
    typeNavn: String,
) {
    val aktivitetId: String = saniter(aktivitetId)
    val arrangør: String = saniter(arrangør)
    val type: String = saniter(type)
    val typeNavn: String = saniter(typeNavn)
    val arenaRegistrertPeriode: Deltakelsesperiode? = arenaRegistrertPeriode
    val periode: Periode = periode

    fun harKunFradatoIArena(): Boolean {
        return arenaRegistrertPeriode?.fra != null && arenaRegistrertPeriode.til == null
    }

    fun harFullstendigPeriodeIArena(): Boolean {
        return arenaRegistrertPeriode?.fra != null && arenaRegistrertPeriode.til != null
    }

    fun søktPeriodeErInnenforArenaRegistrertPeriode(): Boolean {
        val fraDatoIArena = arenaRegistrertPeriode?.fra
        val tilDatoIArena = arenaRegistrertPeriode?.til
        if (harKunFradatoIArena()) {
            return periode.fra.isSameOrAfter(fraDatoIArena!!) && periode.til.isSameOrAfter(fraDatoIArena)
        }
        if (harFullstendigPeriodeIArena()) {
            return periode.fra.isSameOrAfter(fraDatoIArena!!) &&
                periode.fra.isSameOrBefore(tilDatoIArena!!) &&
                periode.til.isSameOrAfter(fraDatoIArena) &&
                periode.til.isSameOrBefore(tilDatoIArena)
        }

        // todo: hvordan validere tiltak som ikke har noen periode i Arena?
        return true
    }

    override fun toString(): String {
        return "Tiltak(periode=$periode, arenaRegistrertPeriode=$arenaRegistrertPeriode)"
    }
}

data class Barnetillegg(
    val manueltRegistrerteBarnSøktBarnetilleggFor: List<ManueltRegistrertBarn>,
    val registrerteBarnSøktBarnetilleggFor: List<RegistrertBarn>,
)

data class Pensjonsordning(
    val mottar: Boolean?,
    val periode: Periode?,
)

data class Etterlønn(
    val mottar: Boolean,
)

data class Sykepenger(
    val mottar: Boolean,
    val periode: Periode?,
)

data class Gjenlevendepensjon(
    val mottar: Boolean?,
    val periode: Periode?,
)

data class Alderspensjon(
    val mottar: Boolean?,
    val fraDato: LocalDate?,
)

data class Supplerendestønadover67(
    val mottar: Boolean?,
    val periode: Periode?,
)

data class Supplerendestønadflyktninger(
    val mottar: Boolean?,
    val periode: Periode?,
)

data class Jobbsjansen(
    val mottar: Boolean?,
    val periode: Periode?,
)

data class SpørsmålsbesvarelserDTO(
    val kvalifiseringsprogram: Kvalifiseringsprogram,
    val introduksjonsprogram: Introduksjonsprogram,
    val institusjonsopphold: Institusjonsopphold,
    val tiltak: Tiltak,
    val barnetillegg: Barnetillegg,
    val mottarAndreUtbetalinger: Boolean,
    val sykepenger: Sykepenger,
    val gjenlevendepensjon: Gjenlevendepensjon,
    val alderspensjon: Alderspensjon,
    val supplerendestønadover67: Supplerendestønadover67,
    val supplerendestønadflyktninger: Supplerendestønadflyktninger,
    val pensjonsordning: Pensjonsordning,
    val etterlønn: Etterlønn,
    val jobbsjansen: Jobbsjansen,
    val harBekreftetAlleOpplysninger: Boolean,
    val harBekreftetÅSvareSåGodtManKan: Boolean,
) {
    fun valider(): List<String> = valider(this)
}

fun String.toSpørsmålsbesvarelserDbJson(): SpørsmålsbesvarelserDTO {
    try {
        return deserialize(this)
    } catch (exception: Exception) {
        throw InvalidParameterException("Det oppstod en feil ved parsing av json for spm: " + exception.message)
    }
}

fun SpørsmålsbesvarelserDTO.toDbJson(): String = serialize(this)
