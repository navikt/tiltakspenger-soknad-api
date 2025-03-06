package no.nav.tiltakspenger.soknad.api.soknad

import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.requestvalidation.ValidationResult
import no.nav.tiltakspenger.soknad.api.isSameOrAfter
import no.nav.tiltakspenger.soknad.api.isSameOrBefore
import java.time.LocalDate

fun RequestValidationConfig.validateSøknad() {
    validate<SpørsmålsbesvarelserDTO> { søknad ->
        val feilmeldinger = valider(søknad)

        if (feilmeldinger.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(feilmeldinger)
        }
    }
}

fun SpørsmålsbesvarelserDTO.validerRequest(): SpørsmålsbesvarelserDTO {
    val feilmeldinger = valider(this)
    if (feilmeldinger.isNotEmpty()) {
        throw RequestValidationException(this, feilmeldinger)
    }
    return this
}

fun validerTiltak(tiltak: Tiltak): List<String> {
    val feilmeldinger = mutableListOf<String>()
    val tiltaksperiode = tiltak.periode
    if (!tiltaksperiode.erGyldig()) {
        feilmeldinger.add("Perioden på tiltaket er ugyldig. Fra-dato må være tidligere enn, eller lik, til-dato.")
    }
    if (!tiltak.søktPeriodeErInnenforArenaRegistrertPeriode()) {
        feilmeldinger.add("Bruker kan ikke søke utenfor den registrerte tiltaksperioden")
    }
    return feilmeldinger
}

fun validerKvalifiseringsprogram(kvalifiseringsprogram: Kvalifiseringsprogram, tiltaksperiode: Periode): List<String> {
    val feilmeldinger = mutableListOf<String>()
    if (kvalifiseringsprogram.deltar) {
        if (kvalifiseringsprogram.periode == null) {
            feilmeldinger.add("Kvalifisering med deltagelse må ha periode")
        } else {
            if (!kvalifiseringsprogram.periode.erGyldig()) {
                feilmeldinger.add("Perioden på KVP er ugyldig. Fra-dato må være tidligere enn, eller lik, til-dato.")
            }
            if (!kvalifiseringsprogram.periode.erGyldigIForholdTilTiltaksperiode(tiltaksperiode)) {
                feilmeldinger.add("Perioden på KVP er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket.")
            }
        }
    } else if (kvalifiseringsprogram.periode != null) {
        feilmeldinger.add("Kvalifiseringsprogram med deltar = false kan ikke ha noen periode")
    }
    return feilmeldinger
}

fun validerIntroduksjonsprogram(introduksjonsprogram: Introduksjonsprogram, tiltaksperiode: Periode): List<String> {
    val feilmeldinger = mutableListOf<String>()
    if (introduksjonsprogram.deltar) {
        if (introduksjonsprogram.periode == null) {
            feilmeldinger.add("Introduksjonsprogram med deltagelse må ha periode")
        } else {
            if (!introduksjonsprogram.periode.erGyldig()) {
                feilmeldinger.add("Perioden på introduksjonsprogram er ugyldig. Fra-dato må være tidligere enn, eller lik, til-dato.")
            }
            if (!introduksjonsprogram.periode.erGyldigIForholdTilTiltaksperiode(tiltaksperiode)) {
                feilmeldinger.add("Perioden på introduksjonsprogram er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket.")
            }
        }
    } else if (introduksjonsprogram.periode != null) {
        feilmeldinger.add("Introduksjonsprogram med deltar = false kan ikke ha noen periode")
    }
    return feilmeldinger
}

fun validerInstitusjonsopphold(institusjonsopphold: Institusjonsopphold, tiltaksperiode: Periode): List<String> {
    val feilmeldinger = mutableListOf<String>()
    if (institusjonsopphold.borPåInstitusjon) {
        if (institusjonsopphold.periode == null) {
            feilmeldinger.add("Institusjonsopphold med deltagelse må ha periode")
        } else {
            if (!institusjonsopphold.periode.erGyldig()) {
                feilmeldinger.add("Perioden på institusjonsopphold er ugyldig. Fra-dato må være tidligere enn, eller lik, til-dato.")
            }
            if (!institusjonsopphold.periode.erGyldigIForholdTilTiltaksperiode(tiltaksperiode)) {
                feilmeldinger.add("Perioden på institusjonsopphold er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket.")
            }
        }
    } else if (institusjonsopphold.periode != null) {
        feilmeldinger.add("Institusjonsopphold med borPåInstitusjon = false kan ikke ha noen periode")
    }
    return feilmeldinger
}

fun validerSykepenger(sykepenger: Sykepenger, tiltaksperiode: Periode): List<String> {
    val feilmeldinger = mutableListOf<String>()
    if (sykepenger.mottar == false) {
        if (sykepenger.periode != null) {
            feilmeldinger.add("Sykepenger med mottar = false kan ikke ha noen periode")
        }
    } else {
        if (sykepenger.periode == null) {
            feilmeldinger.add("Sykepenger med mottar = true må ha periode")
        } else {
            if (!sykepenger.periode.erGyldig()) {
                feilmeldinger.add("Perioden på sykepenger er ugyldig. Fra-dato må være tidligere enn, eller lik, til-dato.")
            }
            if (!sykepenger.periode.erGyldigIForholdTilTiltaksperiode(tiltaksperiode)) {
                feilmeldinger.add("Perioden på sykepenger er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket.")
            }
        }
    }
    return feilmeldinger
}

fun validerPensjonsordning(pensjonsordning: Pensjonsordning, tiltaksperiode: Periode): List<String> {
    val feilmeldinger = mutableListOf<String>()
    if (pensjonsordning.mottar == false) {
        if (pensjonsordning.periode != null) {
            feilmeldinger.add("Pensjonsordning med mottar = false kan ikke ha noen periode")
        }
    } else {
        if (pensjonsordning.periode == null) {
            feilmeldinger.add("Pensjonsordning med mottar = true må ha periode")
        } else {
            if (!pensjonsordning.periode.erGyldig()) {
                feilmeldinger.add("Perioden på pensjonsordning er ugyldig. Fra-dato må være tidligere enn, eller lik, til-dato.")
            }
            if (!pensjonsordning.periode.erGyldigIForholdTilTiltaksperiode(tiltaksperiode)) {
                feilmeldinger.add("Perioden på pensjonsordning er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket.")
            }
        }
    }
    return feilmeldinger
}

fun validerGjenlevendepensjon(gjenlevendepensjon: Gjenlevendepensjon, tiltaksperiode: Periode): List<String> {
    val feilmeldinger = mutableListOf<String>()
    if (gjenlevendepensjon.mottar == false) {
        if (gjenlevendepensjon.periode != null) {
            feilmeldinger.add("Gjenlevendepensjon med mottar = false kan ikke ha noen periode")
        }
    } else {
        if (gjenlevendepensjon.periode == null) {
            feilmeldinger.add("Gjenlevendepensjon med mottar = true må ha periode")
        } else {
            if (!gjenlevendepensjon.periode.erGyldig()) {
                feilmeldinger.add("Perioden på gjenlevendepensjon er ugyldig. Fra-dato må være tidligere enn, eller lik, til-dato.")
            }
            if (!gjenlevendepensjon.periode.erGyldigIForholdTilTiltaksperiode(tiltaksperiode)) {
                feilmeldinger.add("Perioden på gjenlevendepensjon er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket.")
            }
        }
    }
    return feilmeldinger
}

fun validerAlderspensjon(alderspensjon: Alderspensjon, tiltaksperiode: Periode): List<String> {
    val feilmeldinger = mutableListOf<String>()
    if (alderspensjon.mottar == false) {
        if (alderspensjon.fraDato != null) {
            feilmeldinger.add("Alderspensjon med mottar = false kan ikke ha noen fra dato")
        }
    } else {
        if (alderspensjon.fraDato == null) {
            feilmeldinger.add("Alderspensjon med mottar = true må ha fra dato")
        } else {
            if (!alderspensjon.fraDato.erGyldigIForholdTilTiltaksperiode(tiltaksperiode)) {
                feilmeldinger.add("Alderspensjon fra dato kan ikke være senere enn tiltakets periode")
            }
        }
    }
    return feilmeldinger
}

fun validerSupplerendeStønadFlyktninger(supplerendestønadflyktninger: Supplerendestønadflyktninger, tiltaksperiode: Periode): List<String> {
    val feilmeldinger = mutableListOf<String>()
    if (supplerendestønadflyktninger.mottar == false) {
        if (supplerendestønadflyktninger.periode != null) {
            feilmeldinger.add("SupplerendeStønadUføreFlyktninger med mottar = false kan ikke ha noen periode")
        }
    } else {
        if (supplerendestønadflyktninger.periode == null) {
            feilmeldinger.add("SupplerendeStønadUføreFlyktninger med mottar = true må ha periode")
        } else {
            if (!supplerendestønadflyktninger.periode.erGyldig()) {
                feilmeldinger.add("Perioden på SupplerendeStønadUføreFlyktninger er ugyldig. Fra-dato må være tidligere enn, eller lik, til-dato.")
            }
            if (!supplerendestønadflyktninger.periode.erGyldigIForholdTilTiltaksperiode(tiltaksperiode)) {
                feilmeldinger.add("Perioden på SupplerendeStønadUføreFlyktninger er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket.")
            }
        }
    }
    return feilmeldinger
}

fun validerSupplerendeStønadOver67(supplerendestønadover67: Supplerendestønadover67, tiltaksperiode: Periode): List<String> {
    val feilmeldinger = mutableListOf<String>()
    if (supplerendestønadover67.mottar == false) {
        if (supplerendestønadover67.periode != null) {
            feilmeldinger.add("SupplerendeStønadOver67 med mottar = false kan ikke ha noen periode")
        }
    } else {
        if (supplerendestønadover67.periode == null) {
            feilmeldinger.add("SupplerendeStønadOver67 med mottar = true må ha periode")
        } else {
            if (!supplerendestønadover67.periode.erGyldig()) {
                feilmeldinger.add("Perioden på SupplerendeStønadOver67 er ugyldig. Fra-dato må være tidligere enn, eller lik, til-dato.")
            }
            if (!supplerendestønadover67.periode.erGyldigIForholdTilTiltaksperiode(tiltaksperiode)) {
                feilmeldinger.add("Perioden på SupplerendeStønadOver67 er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket.")
            }
        }
    }
    return feilmeldinger
}

fun validerJobbsjansen(jobbsjansen: Jobbsjansen, tiltaksperiode: Periode): List<String> {
    val feilmeldinger = mutableListOf<String>()
    if (jobbsjansen.mottar == false) {
        if (jobbsjansen.periode != null) {
            feilmeldinger.add("Jobbsjansen med mottar = false kan ikke ha noen periode")
        }
    } else {
        if (jobbsjansen.periode == null) {
            feilmeldinger.add("Jobbsjansen med mottar = true må ha periode")
        } else {
            if (!jobbsjansen.periode.erGyldig()) {
                feilmeldinger.add("Perioden på Jobbsjansen er ugyldig. Fra-dato må være tidligere enn, eller lik, til-dato.")
            }
            if (!jobbsjansen.periode.erGyldigIForholdTilTiltaksperiode(tiltaksperiode)) {
                feilmeldinger.add("Perioden på Jobbsjansen er ugyldig. Perioden kan ikke gå utenfor perioden på tiltaket.")
            }
        }
    }
    return feilmeldinger
}

fun Periode.erGyldigIForholdTilTiltaksperiode(tiltaksperiode: Periode): Boolean {
    return this.erInnenfor(tiltaksperiode)
}

fun LocalDate.erGyldigIForholdTilTiltaksperiode(tiltaksperiode: Periode): Boolean {
    return this.isSameOrAfter(tiltaksperiode.fra) && this.isSameOrBefore(tiltaksperiode.til)
}

fun validerAndreUtbetalinger(søknad: SpørsmålsbesvarelserDTO, tiltaksperiode: Periode): List<String> {
    val feilmeldinger = mutableListOf<String>()

    if (søknad.mottarAndreUtbetalinger == true) {
        feilmeldinger.addAll(validerPensjonsordning(søknad.pensjonsordning, tiltaksperiode))
        feilmeldinger.addAll(validerGjenlevendepensjon(søknad.gjenlevendepensjon, tiltaksperiode))
        feilmeldinger.addAll(validerAlderspensjon(søknad.alderspensjon, tiltaksperiode))
        feilmeldinger.addAll(validerSupplerendeStønadFlyktninger(søknad.supplerendestønadflyktninger, tiltaksperiode))
        feilmeldinger.addAll(validerSupplerendeStønadOver67(søknad.supplerendestønadover67, tiltaksperiode))
        feilmeldinger.addAll(validerJobbsjansen(søknad.jobbsjansen, tiltaksperiode))
    }

    return feilmeldinger
}

val navnMaxLength = 1000

fun validerBarnetillegg(barnetillegg: Barnetillegg): List<String> {
    val feilmeldinger = mutableListOf<String>()
    val manueltRegistrerteBarn = barnetillegg.manueltRegistrerteBarnSøktBarnetilleggFor

    val harEtBarnMedForLangtNavn = manueltRegistrerteBarn.any {
        (it.fornavn.length > navnMaxLength) || (it.etternavn.length > navnMaxLength) || ((it.mellomnavn != null) && (it.mellomnavn.length > navnMaxLength))
    }
    val harEtBarnMedFødselsdatoFramITid = manueltRegistrerteBarn.any { it.fødselsdato.isSameOrAfter(LocalDate.now().plusDays(1)) }
    if (harEtBarnMedForLangtNavn) {
        feilmeldinger.add("Manuelt registrert barn er ugyldig: fornavn, mellomnavn eller etternavn overskrider maksgrense på 1000 tegn")
    }
    if (harEtBarnMedFødselsdatoFramITid) {
        feilmeldinger.add("Manuelt registrert barn er ugyldig: fødselsdato kan ikke registreres fram i tid")
    }

    return feilmeldinger
}

fun valider(søknad: SpørsmålsbesvarelserDTO): List<String> {
    val feilmeldinger = mutableListOf<String>()

    if (søknad.harBekreftetAlleOpplysninger == false) {
        feilmeldinger.add("Bruker må bekrefte å ha oppgitt riktige opplysninger")
    }

    if (søknad.harBekreftetÅSvareSåGodtManKan == false) {
        feilmeldinger.add("Bruker må bekrefte å svare så godt man kan")
    }

    val tiltaksperiode = søknad.tiltak.periode
    feilmeldinger.addAll(validerTiltak(søknad.tiltak))
    feilmeldinger.addAll(validerKvalifiseringsprogram(søknad.kvalifiseringsprogram, tiltaksperiode))
    feilmeldinger.addAll(validerIntroduksjonsprogram(søknad.introduksjonsprogram, tiltaksperiode))
    feilmeldinger.addAll(validerInstitusjonsopphold(søknad.institusjonsopphold, tiltaksperiode))
    feilmeldinger.addAll(validerSykepenger(søknad.sykepenger, tiltaksperiode))
    feilmeldinger.addAll(validerAndreUtbetalinger(søknad, tiltaksperiode))
    feilmeldinger.addAll(validerBarnetillegg(søknad.barnetillegg))

    return feilmeldinger
}
