package no.nav.tiltakspenger.soknad.api.saksbehandlingApi

import no.nav.tiltakspenger.libs.soknad.BarnetilleggDTO
import no.nav.tiltakspenger.libs.soknad.FraOgMedDatoSpmDTO
import no.nav.tiltakspenger.libs.soknad.JaNeiSpmDTO
import no.nav.tiltakspenger.libs.soknad.PeriodeSpmDTO
import no.nav.tiltakspenger.libs.soknad.PersonopplysningerDTO
import no.nav.tiltakspenger.libs.soknad.SpmSvarDTO.Ja
import no.nav.tiltakspenger.libs.soknad.SpmSvarDTO.Nei
import no.nav.tiltakspenger.libs.soknad.SøknadDTO
import no.nav.tiltakspenger.libs.soknad.SøknadsTiltakDTO
import no.nav.tiltakspenger.soknad.api.domain.Søknad
import no.nav.tiltakspenger.soknad.api.soknad.Alderspensjon
import no.nav.tiltakspenger.soknad.api.soknad.Gjenlevendepensjon
import no.nav.tiltakspenger.soknad.api.soknad.Jobbsjansen
import no.nav.tiltakspenger.soknad.api.soknad.Pensjonsordning
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import no.nav.tiltakspenger.soknad.api.soknad.Supplerendestønadflyktninger
import no.nav.tiltakspenger.soknad.api.soknad.Supplerendestønadover67
import java.time.LocalDate

fun søknadMapper(
    søknad: Søknad,
    jounalpostId: String,
    saksnummer: String,
): SøknadDTO {
    val soknad = if (!søknad.spørsmålsbesvarelser.mottarAndreUtbetalinger) {
        søknad.copy(
            spørsmålsbesvarelser = søknad.spørsmålsbesvarelser.copy(
                gjenlevendepensjon = Gjenlevendepensjon(false, null),
                alderspensjon = Alderspensjon(false, null),
                supplerendestønadflyktninger = Supplerendestønadflyktninger(false, null),
                supplerendestønadover67 = Supplerendestønadover67(false, null),
                jobbsjansen = Jobbsjansen(false, null),
                pensjonsordning = Pensjonsordning(false, null),
            ),
        )
    } else {
        søknad
    }

    return SøknadDTO(
        søknadId = soknad.id,
        versjon = soknad.versjon,
        journalpostId = jounalpostId,
        personopplysninger = PersonopplysningerDTO(
            ident = soknad.personopplysninger.ident,
            fornavn = soknad.personopplysninger.fornavn,
            etternavn = soknad.personopplysninger.etternavn,
        ),
        tiltak = SøknadsTiltakDTO(
            id = soknad.spørsmålsbesvarelser.tiltak.aktivitetId,
            deltakelseFom = soknad.spørsmålsbesvarelser.tiltak.periode.fra,
            deltakelseTom = soknad.spørsmålsbesvarelser.tiltak.periode.til,
            arrangør = soknad.spørsmålsbesvarelser.tiltak.arrangør,
            typeKode = soknad.spørsmålsbesvarelser.tiltak.type,
            typeNavn = soknad.spørsmålsbesvarelser.tiltak.typeNavn,
        ),
        barnetilleggPdl = soknad.spørsmålsbesvarelser.barnetillegg.registrerteBarnSøktBarnetilleggFor.map {
            BarnetilleggDTO(
                fødselsdato = it.fødselsdato,
                fornavn = it.fornavn,
                mellomnavn = it.mellomnavn,
                etternavn = it.etternavn,
                oppholderSegIEØS = JaNeiSpmDTO(
                    svar = if (it.oppholdInnenforEøs) Ja else Nei,
                ),
                adressebeskyttelseDTO = it.adressebeskyttelse.toAdresseBeskyttelseDTO(),
            )
        },
        barnetilleggManuelle = soknad.spørsmålsbesvarelser.barnetillegg.manueltRegistrerteBarnSøktBarnetilleggFor.map {
            BarnetilleggDTO(
                fødselsdato = it.fødselsdato,
                fornavn = it.fornavn,
                mellomnavn = it.mellomnavn,
                etternavn = it.etternavn,
                oppholderSegIEØS = JaNeiSpmDTO(
                    svar = if (it.oppholdInnenforEøs) Ja else Nei,
                ),
                adressebeskyttelseDTO = it.adressebeskyttelse.toAdresseBeskyttelseDTO(),
            )
        },
        vedlegg = søknad.vedleggsnavn.size,
        kvp = mapPeriodeSpm(
            mottar = soknad.spørsmålsbesvarelser.kvalifiseringsprogram.deltar,
            periode = soknad.spørsmålsbesvarelser.kvalifiseringsprogram.periode,
        ),
        intro = mapPeriodeSpm(
            mottar = soknad.spørsmålsbesvarelser.introduksjonsprogram.deltar,
            periode = soknad.spørsmålsbesvarelser.introduksjonsprogram.periode,
        ),
        institusjon = mapPeriodeSpm(
            mottar = soknad.spørsmålsbesvarelser.institusjonsopphold.borPåInstitusjon,
            periode = soknad.spørsmålsbesvarelser.institusjonsopphold.periode,
        ),
        etterlønn = JaNeiSpmDTO(
            svar = if (soknad.spørsmålsbesvarelser.etterlønn.mottar) Ja else Nei,
        ),
        gjenlevendepensjon = mapPeriodeSpm(
            mottar = soknad.spørsmålsbesvarelser.gjenlevendepensjon.mottar!!,
            periode = soknad.spørsmålsbesvarelser.gjenlevendepensjon.periode,
        ),
        alderspensjon = mapFraOgMedSpm(
            mottar = soknad.spørsmålsbesvarelser.alderspensjon.mottar!!,
            fraDato = soknad.spørsmålsbesvarelser.alderspensjon.fraDato,
        ),
        sykepenger = mapPeriodeSpm(
            mottar = soknad.spørsmålsbesvarelser.sykepenger.mottar,
            periode = soknad.spørsmålsbesvarelser.sykepenger.periode,
        ),
        supplerendeStønadAlder = mapPeriodeSpm(
            mottar = soknad.spørsmålsbesvarelser.supplerendestønadover67.mottar!!,
            periode = soknad.spørsmålsbesvarelser.supplerendestønadover67.periode,
        ),
        supplerendeStønadFlyktning = mapPeriodeSpm(
            mottar = soknad.spørsmålsbesvarelser.supplerendestønadflyktninger.mottar!!,
            periode = soknad.spørsmålsbesvarelser.supplerendestønadflyktninger.periode,
        ),
        jobbsjansen = mapPeriodeSpm(
            mottar = soknad.spørsmålsbesvarelser.jobbsjansen.mottar!!,
            periode = soknad.spørsmålsbesvarelser.jobbsjansen.periode,
        ),
        trygdOgPensjon = mapPeriodeSpm(
            mottar = soknad.spørsmålsbesvarelser.pensjonsordning.mottar!!,
            periode = soknad.spørsmålsbesvarelser.pensjonsordning.periode,
        ),
        opprettet = soknad.innsendingTidspunkt,
        saksnummer = saksnummer,
    )
}

private fun mapPeriodeSpm(mottar: Boolean, periode: Periode?) =
    if (mottar) {
        PeriodeSpmDTO(
            svar = Ja,
            fom = periode?.fra,
            tom = periode?.til,
        )
    } else {
        PeriodeSpmDTO(
            svar = Nei,
            fom = null,
            tom = null,
        )
    }

private fun mapFraOgMedSpm(mottar: Boolean, fraDato: LocalDate?) =
    if (mottar) {
        FraOgMedDatoSpmDTO(
            svar = Ja,
            fom = fraDato,
        )
    } else {
        FraOgMedDatoSpmDTO(
            svar = Nei,
            fom = null,
        )
    }
