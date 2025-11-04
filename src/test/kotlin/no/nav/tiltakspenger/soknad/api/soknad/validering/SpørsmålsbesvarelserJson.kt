package no.nav.tiltakspenger.soknad.api.soknad.validering

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import no.nav.tiltakspenger.soknad.api.soknad.Alderspensjon
import no.nav.tiltakspenger.soknad.api.soknad.Barnetillegg
import no.nav.tiltakspenger.soknad.api.soknad.Etterlønn
import no.nav.tiltakspenger.soknad.api.soknad.Gjenlevendepensjon
import no.nav.tiltakspenger.soknad.api.soknad.Institusjonsopphold
import no.nav.tiltakspenger.soknad.api.soknad.Introduksjonsprogram
import no.nav.tiltakspenger.soknad.api.soknad.Jobbsjansen
import no.nav.tiltakspenger.soknad.api.soknad.Kvalifiseringsprogram
import no.nav.tiltakspenger.soknad.api.soknad.ManueltRegistrertBarn
import no.nav.tiltakspenger.soknad.api.soknad.Pensjonsordning
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import no.nav.tiltakspenger.soknad.api.soknad.RegistrertBarn
import no.nav.tiltakspenger.soknad.api.soknad.SpørsmålsbesvarelserDTO
import no.nav.tiltakspenger.soknad.api.soknad.Supplerendestønadflyktninger
import no.nav.tiltakspenger.soknad.api.soknad.Supplerendestønadover67
import no.nav.tiltakspenger.soknad.api.soknad.Sykepenger
import no.nav.tiltakspenger.soknad.api.soknad.Tiltak
import java.time.LocalDate
import java.util.UUID

fun Any.toJsonString(): String {
    val objectMapper = ObjectMapper().configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false).registerModule(
        JavaTimeModule(),
    )
    return objectMapper.writeValueAsString(this)
}

fun defaultPeriode() = Periode(
    fra = LocalDate.of(2025, 1, 1),
    til = LocalDate.of(2025, 1, 1),
)

private fun tiltak() = Tiltak(
    aktivitetId = "123",
    periode = defaultPeriode(),
    arrangør = "test",
    type = "test",
    typeNavn = "test",
    arenaRegistrertPeriode = null,
    gjennomforingId = UUID.randomUUID().toString(),
)

fun spørsmålsbesvarelser(
    tiltak: Tiltak = tiltak(),
    barnetillegg: Barnetillegg = barnetillegg(),
    institusjonsopphold: Institusjonsopphold = institusjonsopphold(),
    introduksjonsprogram: Introduksjonsprogram = introduksjonsprogram(),
    kvalifiseringsprogram: Kvalifiseringsprogram = kvalifiseringsprogram(),
    pensjonsordning: Pensjonsordning = pensjonsordning(),
    mottarAndreUtbetalinger: Boolean = true,
    sykepenger: Sykepenger = sykepenger(),
    gjenlevendepensjon: Gjenlevendepensjon = gjenlevendepensjon(),
    alderspensjon: Alderspensjon = alderspensjon(),
    supplerendestønadover67: Supplerendestønadover67 = supplerendestønadover67år(),
    supplerendestønadflyktninger: Supplerendestønadflyktninger = supplerendestønadflyktninger(),
    etterlønn: Etterlønn = etterlønn(),
    jobbsjansen: Jobbsjansen = jobbsjansen(),
    harBekreftetAlleOpplysninger: Boolean = true,
    harBekreftetÅSvareSåGodtManKan: Boolean = true,
): SpørsmålsbesvarelserDTO = SpørsmålsbesvarelserDTO(
    tiltak = tiltak,
    barnetillegg = barnetillegg,
    institusjonsopphold = institusjonsopphold,
    introduksjonsprogram = introduksjonsprogram,
    kvalifiseringsprogram = kvalifiseringsprogram,
    pensjonsordning = pensjonsordning,
    mottarAndreUtbetalinger = mottarAndreUtbetalinger,
    sykepenger = sykepenger,
    gjenlevendepensjon = gjenlevendepensjon,
    alderspensjon = alderspensjon,
    supplerendestønadover67 = supplerendestønadover67,
    supplerendestønadflyktninger = supplerendestønadflyktninger,
    etterlønn = etterlønn,
    jobbsjansen = jobbsjansen,
    harBekreftetAlleOpplysninger = harBekreftetAlleOpplysninger,
    harBekreftetÅSvareSåGodtManKan = harBekreftetÅSvareSåGodtManKan,
)

private fun barnetillegg(): Barnetillegg = Barnetillegg(
    manueltRegistrerteBarnSøktBarnetilleggFor = listOf(
        ManueltRegistrertBarn(
            fornavn = "Test",
            mellomnavn = "Test",
            etternavn = "Test",
            fødselsdato = LocalDate.of(2023, 1, 1),
            oppholdInnenforEøs = true,
        ),
    ),
    registrerteBarnSøktBarnetilleggFor = listOf(
        RegistrertBarn(
            fornavn = "Test",
            fødselsdato = LocalDate.of(2023, 1, 1),
            etternavn = "Testesen",
            mellomnavn = "Test",
            oppholdInnenforEøs = true,
            adressebeskyttelse = no.nav.tiltakspenger.soknad.api.pdl.routes.dto.AdressebeskyttelseDTO.UGRADERT,
        ),
    ),
)

private fun etterlønn(): Etterlønn = Etterlønn(mottar = true)

private fun institusjonsopphold() = Institusjonsopphold(
    borPåInstitusjon = true,
    periode = defaultPeriode(),
)

private fun introduksjonsprogram() = Introduksjonsprogram(
    deltar = true,
    periode = defaultPeriode(),
)

private fun kvalifiseringsprogram() =
    Kvalifiseringsprogram(
        deltar = true,
        periode = defaultPeriode(),
    )

private fun sykepenger() =
    Sykepenger(
        mottar = true,
        periode = defaultPeriode(),
    )

private fun gjenlevendepensjon() =
    Gjenlevendepensjon(
        mottar = true,
        periode = defaultPeriode(),
    )

private fun supplerendestønadover67år() =
    Supplerendestønadover67(
        mottar = true,
        periode = defaultPeriode(),
    )

private fun supplerendestønadflyktninger() =
    Supplerendestønadflyktninger(
        mottar = true,
        periode = defaultPeriode(),
    )

private fun jobbsjansen() =
    Jobbsjansen(
        mottar = true,
        periode = defaultPeriode(),
    )

private fun alderspensjon() =
    Alderspensjon(
        mottar = true,
        fraDato = LocalDate.of(2025, 1, 1),
    )

private fun pensjonsordning() =
    Pensjonsordning(
        mottar = true,
        periode = defaultPeriode(),
    )
