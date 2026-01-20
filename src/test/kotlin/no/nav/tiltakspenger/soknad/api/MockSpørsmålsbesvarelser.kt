package no.nav.tiltakspenger.soknad.api

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
import no.nav.tiltakspenger.soknad.api.tiltak.Deltakelsesperiode
import java.time.LocalDate
import java.util.UUID

fun mockTiltak(
    aktivitetId: String = "123",
    periode: Periode = Periode(
        fra = LocalDate.of(2025, 1, 1),
        til = LocalDate.of(2025, 1, 1),
    ),
    arenaRegistrertPeriode: Deltakelsesperiode = Deltakelsesperiode(
        fra = LocalDate.of(2025, 1, 1),
        til = LocalDate.of(2025, 1, 31),
    ),
    arrangør: String = "test",
    type: String = "test",
    typeNavn: String = "test",
    gjennomforingId: String = UUID.randomUUID().toString(),
): Tiltak =
    Tiltak(
        aktivitetId = aktivitetId,
        periode = periode,
        arrangør = arrangør,
        type = type,
        typeNavn = typeNavn,
        arenaRegistrertPeriode = arenaRegistrertPeriode,
        gjennomforingId = gjennomforingId,
        visningsnavn = "$typeNavn hos $arrangør",
    )

fun mockKvalifiseringsprogram(
    deltar: Boolean = false,
    periode: Periode? = null,
): Kvalifiseringsprogram =
    Kvalifiseringsprogram(deltar, periode)

fun mockIntroduksjonsprogram(
    deltar: Boolean = false,
    periode: Periode? = null,
): Introduksjonsprogram =
    Introduksjonsprogram(deltar, periode)

fun mockInstitusjonsopphold(
    borPåInstitusjon: Boolean = false,
    periode: Periode? = null,
): Institusjonsopphold =
    Institusjonsopphold(borPåInstitusjon, periode)

fun mockSykepenger(
    mottar: Boolean = false,
    periode: Periode? = null,
): Sykepenger =
    Sykepenger(mottar, periode)

fun mockGjenlevendepensjon(
    mottar: Boolean = false,
    periode: Periode? = null,
): Gjenlevendepensjon =
    Gjenlevendepensjon(mottar, periode)

fun mockAlderspensjon(
    mottar: Boolean = false,
    fraDato: LocalDate? = null,
): Alderspensjon =
    Alderspensjon(mottar, fraDato)

fun mockSupplerendestønadover67(
    mottar: Boolean = false,
    periode: Periode? = null,
): Supplerendestønadover67 =
    Supplerendestønadover67(mottar, periode)

fun mockSupplerendestønadflyktninger(
    mottar: Boolean = false,
    periode: Periode? = null,
): Supplerendestønadflyktninger =
    Supplerendestønadflyktninger(mottar, periode)

fun mockPensjonsordning(
    mottar: Boolean = false,
    periode: Periode? = null,
): Pensjonsordning =
    Pensjonsordning(mottar, periode)

fun mockEtterlønn(
    mottar: Boolean = false,
): Etterlønn =
    Etterlønn(mottar)

fun mockJobbsjansen(
    mottar: Boolean = false,
    periode: Periode? = null,
): Jobbsjansen =
    Jobbsjansen(mottar, periode)

fun mockManueltRegistrertBarn(
    fornavn: String = "Test",
    etternavn: String = "Testesen",
    mellomnavn: String? = null,
    fødselsdato: LocalDate = LocalDate.now(),
    oppholdInnenforEøs: Boolean = true,
): ManueltRegistrertBarn =
    ManueltRegistrertBarn(
        fornavn,
        mellomnavn,
        etternavn,
        fødselsdato,
        oppholdInnenforEøs,
    )

fun mockRegistrertBarn(
    fnr: String = "12345678910",
    fornavn: String = "Test",
    etternavn: String = "Testesen",
    mellomnavn: String? = null,
    fødselsdato: LocalDate = LocalDate.of(2025, 1, 1),
    oppholdInnenforEøs: Boolean = true,
): RegistrertBarn =
    RegistrertBarn(
        fnr,
        fornavn,
        mellomnavn,
        etternavn,
        fødselsdato,
        oppholdInnenforEøs,
    )

fun mockBarnetillegg(
    manueltRegistrerteBarnSøktBarnetilleggFor: List<ManueltRegistrertBarn> =
        listOf(mockManueltRegistrertBarn()),
    registrerteBarnSøktBarnetilleggFor: List<RegistrertBarn> =
        listOf(mockRegistrertBarn()),
): Barnetillegg =
    Barnetillegg(
        manueltRegistrerteBarnSøktBarnetilleggFor = manueltRegistrerteBarnSøktBarnetilleggFor,
        registrerteBarnSøktBarnetilleggFor = registrerteBarnSøktBarnetilleggFor,
    )

fun mockSpørsmålsbesvarelser(
    tiltak: Tiltak = mockTiltak(),
    kvalifiseringsprogram: Kvalifiseringsprogram = mockKvalifiseringsprogram(),
    introduksjonsprogram: Introduksjonsprogram = mockIntroduksjonsprogram(),
    institusjonsopphold: Institusjonsopphold = mockInstitusjonsopphold(),
    barnetillegg: Barnetillegg = mockBarnetillegg(),
    mottarAndreUtbetalinger: Boolean = false,
    sykepenger: Sykepenger = mockSykepenger(),
    gjenlevendepensjon: Gjenlevendepensjon = mockGjenlevendepensjon(),
    alderspensjon: Alderspensjon = mockAlderspensjon(),
    supplerendestønadover67: Supplerendestønadover67 = mockSupplerendestønadover67(),
    supplerendestønadflyktninger: Supplerendestønadflyktninger = mockSupplerendestønadflyktninger(),
    pensjonsordning: Pensjonsordning = mockPensjonsordning(),
    etterlønn: Etterlønn = mockEtterlønn(),
    jobbsjansen: Jobbsjansen = mockJobbsjansen(),
    harBekreftetAlleOpplysninger: Boolean = true,
    harBekreftetÅSvareSåGodtManKan: Boolean = true,
): SpørsmålsbesvarelserDTO = SpørsmålsbesvarelserDTO(
    tiltak = tiltak,
    kvalifiseringsprogram = kvalifiseringsprogram,
    introduksjonsprogram = introduksjonsprogram,
    institusjonsopphold = institusjonsopphold,
    barnetillegg = barnetillegg,
    mottarAndreUtbetalinger = mottarAndreUtbetalinger,
    sykepenger = sykepenger,
    gjenlevendepensjon = gjenlevendepensjon,
    alderspensjon = alderspensjon,
    supplerendestønadover67 = supplerendestønadover67,
    supplerendestønadflyktninger = supplerendestønadflyktninger,
    pensjonsordning = pensjonsordning,
    etterlønn = etterlønn,
    jobbsjansen = jobbsjansen,
    harBekreftetAlleOpplysninger = harBekreftetAlleOpplysninger,
    harBekreftetÅSvareSåGodtManKan = harBekreftetÅSvareSåGodtManKan,
)
