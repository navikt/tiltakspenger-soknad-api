package no.nav.tiltakspenger.soknad.api.tiltak

import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.TiltakTypeDTO
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltaksdeltakelse
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TiltakstypeSomGirRett
import java.time.Clock
import java.time.LocalDate

data class Deltakelsesperiode(
    val fra: LocalDate?,
    val til: LocalDate?,
)

/**
 * En tiltaksdeltakelse slik søknadsfrontenden får den servert på `/tiltak`.
 *
 * Formen er kontrakten mot frontenden, og den samme formen sendes tilbake til oss i den innsendte søknaden.
 * Feltnavnene bærer fortsatt Arena-vokabularet (`aktivitetId`, `arenaRegistrertPeriode`) fordi de står i frontendens typer og i søknadens JSON.
 */
data class TiltaksdeltakelseDto(
    val aktivitetId: String,
    val type: TiltakTypeDTO,
    val typeNavn: String,
    val arenaRegistrertPeriode: Deltakelsesperiode,
    val arrangør: String,
    val gjennomforingId: String,
    val visningsnavn: String?,
) {
    /**
     * Tidsrommet søknaden viser tiltaksdeltakelser fra: seks måneder tilbake til to måneder fram.
     * En deltakelse uten startdato slipper alltid gjennom — vi vet ikke når den hører hjemme, og skjuler den derfor ikke.
     */
    fun erInnenforRelevantTidsrom(clock: Clock): Boolean {
        val iDag = LocalDate.now(clock)
        val datoFor6MånederSiden = iDag.minusMonths(6)
        val dato2MånederFrem = iDag.plusMonths(2)
        val fra = arenaRegistrertPeriode.fra
        val til = arenaRegistrertPeriode.til

        return if (fra == null) {
            true
        } else if (til == null) {
            fra.isBefore(dato2MånederFrem) && fra.isAfter(datoFor6MånederSiden)
        } else {
            fra.isBefore(dato2MånederFrem) && til.isAfter(datoFor6MånederSiden)
        }
    }
}

/**
 * Mapper deltakelsene kilden tilsier at bruker kan søke på til formen frontenden venter.
 *
 * [maskerArrangørnavn] gjelder søkere med adressebeskyttelse: arrangørnavnet og den sammensatte tittelen peker ut hvor personen møter opp, så begge erstattes av tiltakstypenavnet alene.
 * `arrangør` og `gjennomforingId` er ikke-nullable på wiren, og faller derfor tilbake på de samme plassholderne dagens frontend allerede håndterer: «Ukjent» og tom streng.
 */
fun List<Tiltaksdeltakelse.GirRett>.toTiltakDto(maskerArrangørnavn: Boolean): List<TiltaksdeltakelseDto> = this.map {
    TiltaksdeltakelseDto(
        aktivitetId = it.id.verdi,
        type = it.tiltakstype.tilArenakode(),
        typeNavn = it.tiltakstypenavn,
        arenaRegistrertPeriode = Deltakelsesperiode(
            fra = it.fraOgMed,
            til = it.tilOgMed,
        ),
        arrangør = if (maskerArrangørnavn) "" else (it.arrangør.hovedenhet ?: it.arrangør.underenhet)?.verdi ?: "Ukjent",
        gjennomforingId = it.gjennomføringId?.verdi ?: "",
        visningsnavn = if (maskerArrangørnavn) it.tiltakstypenavn else it.tittel?.verdi,
    )
}

/**
 * Arena-koden for tiltakstypen.
 *
 * Domenetypen er vår egen klassifisering, mens `type` på wiren har vært Arena-koden siden tiltakspenger-tiltak leverte den.
 * Frontenden sender verdien uendret tilbake i søknaden, og saksbehandling-api leser den med `TiltakResponsDTO.TiltakTypeDTO.valueOf` — en annen verdi ville felt innsendingen der.
 * Mappingen er den eksakte inversen av `TiltakTypeDTO.toTiltakstypeSomGirRett()` i libs, og `TiltaksdeltakelseDtoTest` pinner både verdiene og at den fortsatt er inversen.
 */
fun TiltakstypeSomGirRett.tilArenakode(): TiltakTypeDTO = when (this) {
    TiltakstypeSomGirRett.ARBEIDSFORBEREDENDE_TRENING -> TiltakTypeDTO.ARBFORB
    TiltakstypeSomGirRett.ARBEIDSMARKEDSOPPLAERING -> TiltakTypeDTO.ARBEIDSMARKEDSOPPLAERING
    TiltakstypeSomGirRett.ARBEIDSRETTET_REHABILITERING -> TiltakTypeDTO.ARBRRHDAG
    TiltakstypeSomGirRett.ARBEIDSTRENING -> TiltakTypeDTO.ARBTREN
    TiltakstypeSomGirRett.AVKLARING -> TiltakTypeDTO.AVKLARAG
    TiltakstypeSomGirRett.DIGITAL_JOBBKLUBB -> TiltakTypeDTO.DIGIOPPARB
    TiltakstypeSomGirRett.ENKELTPLASS_AMO -> TiltakTypeDTO.ENKELAMO
    TiltakstypeSomGirRett.ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG -> TiltakTypeDTO.ENKFAGYRKE
    TiltakstypeSomGirRett.FAG_OG_YRKESOPPLAERING -> TiltakTypeDTO.FAG_OG_YRKESOPPLAERING
    TiltakstypeSomGirRett.FORSØK_OPPLÆRING_LENGRE_VARIGHET -> TiltakTypeDTO.FORSOPPLEV
    TiltakstypeSomGirRett.GRUPPE_AMO -> TiltakTypeDTO.GRUPPEAMO
    TiltakstypeSomGirRett.GRUPPE_VGS_OG_HØYERE_YRKESFAG -> TiltakTypeDTO.GRUFAGYRKE
    TiltakstypeSomGirRett.HØYERE_UTDANNING -> TiltakTypeDTO.HOYEREUTD
    TiltakstypeSomGirRett.HOYERE_YRKESFAGLIG_UTDANNING -> TiltakTypeDTO.HOYERE_YRKESFAGLIG_UTDANNING
    TiltakstypeSomGirRett.INDIVIDUELL_JOBBSTØTTE -> TiltakTypeDTO.INDJOBSTOT
    TiltakstypeSomGirRett.INDIVIDUELL_KARRIERESTØTTE_UNG -> TiltakTypeDTO.IPSUNG
    TiltakstypeSomGirRett.JOBBKLUBB -> TiltakTypeDTO.JOBBK
    TiltakstypeSomGirRett.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV -> TiltakTypeDTO.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV
    TiltakstypeSomGirRett.OPPFØLGING -> TiltakTypeDTO.INDOPPFAG
    TiltakstypeSomGirRett.STUDIESPESIALISERING -> TiltakTypeDTO.STUDIESPESIALISERING
    TiltakstypeSomGirRett.UTVIDET_OPPFØLGING_I_NAV -> TiltakTypeDTO.UTVAOONAV
    TiltakstypeSomGirRett.UTVIDET_OPPFØLGING_I_OPPLÆRING -> TiltakTypeDTO.UTVOPPFOPL
}
