package no.nav.tiltakspenger.soknad.api.tiltak

import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.TiltakTypeDTO
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltaksdeltakelse
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TiltakstypeSomGirRett
import no.nav.tiltakspenger.soknad.api.soknad.Behandlingsresultat
import no.nav.tiltakspenger.soknad.api.soknad.Behandlingsutfall
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import no.nav.tiltakspenger.soknad.api.soknad.SpørsmålsbesvarelserDTO
import no.nav.tiltakspenger.soknad.api.soknad.TidligereSøknad
import no.nav.tiltakspenger.soknad.api.soknad.Tiltak
import no.nav.tiltakspenger.soknad.api.soknad.sisteVedtakSomDekker
import no.nav.tiltakspenger.soknad.api.soknad.søknadSomDekker
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

data class Deltakelsesperiode(
    val fra: LocalDate?,
    val til: LocalDate?,
)

/**
 * En søknad vi allerede har mottatt for deltakelsen, se [TiltaksdeltakelseDto.medTidligereSøknad].
 *
 * @property svar Svarene i den tidligere søknaden, i samme form som frontenden sender dem inn.
 * Frontenden viser dem før bruker søker på nytt, fyller ut den nye søknaden med dem og markerer hva som er endret.
 * @property behandlingsutfall Utfallet av behandlingen, eller null om søknaden ikke er ferdig behandlet.
 * En ferdig behandlet søknad kan sendes inn på nytt med de samme svarene.
 * En gjenåpnet søknad er under behandling igjen, og har derfor ikke noe utfall her.
 */
data class TidligereSøknadDto(
    val mottatt: LocalDateTime,
    val svar: SpørsmålsbesvarelserDTO,
    val behandlingsutfall: BehandlingsutfallDto?,
)

/**
 * @property tidspunkt Når vedtaket ble opprettet, eller når søknaden ble avbrutt.
 */
data class BehandlingsutfallDto(
    val resultat: BehandlingsresultatDto,
    val tidspunkt: LocalDateTime,
)

/**
 * Det nyeste vedtaket på en søknad for deltakelsen, se [TiltaksdeltakelseDto.medTidligereSøknad].
 *
 * @property søknadMottatt Når vi mottok søknaden vedtaket gjelder.
 * Det er alltid en eldre søknad enn [TiltaksdeltakelseDto.tidligereSøknad].
 * @property behandlingsutfall Vedtaket, alltid innvilgelse eller avslag.
 */
data class SisteVedtakDto(
    val søknadMottatt: LocalDateTime,
    val behandlingsutfall: BehandlingsutfallDto,
)

enum class BehandlingsresultatDto {
    INNVILGELSE,
    AVSLAG,
    AVBRUTT,
}

private fun Behandlingsutfall.toDto(): BehandlingsutfallDto? {
    val resultat = when (resultat) {
        Behandlingsresultat.INNVILGELSE -> BehandlingsresultatDto.INNVILGELSE
        Behandlingsresultat.AVSLAG -> BehandlingsresultatDto.AVSLAG
        Behandlingsresultat.AVBRUTT -> BehandlingsresultatDto.AVBRUTT
        Behandlingsresultat.GJENÅPNET -> return null
    }
    return BehandlingsutfallDto(resultat = resultat, tidspunkt = tidspunkt)
}

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
    val tidligereSøknad: TidligereSøknadDto?,
    val sisteVedtak: SisteVedtakDto?,
) {
    /**
     * Søknaden setter søkt periode lik deltakelsens registrerte periode.
     * Er den perioden allerede dekket av en tidligere søknad for samme deltakelse, får deltakelsen med seg den sist mottatte av dem.
     * En ny søknad sammenlignes med akkurat den, og avvises om svarene er de samme (se [no.nav.tiltakspenger.soknad.api.soknad.duplikatAv]).
     * Har ikke den søknaden fått vedtak, får deltakelsen i tillegg med det nyeste vedtaket på de eldre søknadene som dekker perioden, som [sisteVedtak].
     * Da er søknaden under behandling, avbrutt eller gjenåpnet, og bruker skal fortsatt se vedtaket som gjelder.
     * En deltakelse uten fullstendig periode kan ikke søkes digitalt, og får derfor aldri [tidligereSøknad].
     * Beskrivelsen av tiltaket i svarene erstattes med deltakelsens, se [medBeskrivelseFra].
     */
    fun medTidligereSøknad(tidligereSøknader: List<TidligereSøknad>): TiltaksdeltakelseDto {
        val fra = arenaRegistrertPeriode.fra ?: return this
        val til = arenaRegistrertPeriode.til ?: return this
        val tidligere = tidligereSøknader.søknadSomDekker(aktivitetId, Periode(fra = fra, til = til)) ?: return this
        val svar = tidligere.svar.copy(tiltak = tidligere.svar.tiltak.medBeskrivelseFra(this))
        return copy(
            tidligereSøknad = TidligereSøknadDto(
                mottatt = tidligere.mottatt,
                svar = svar,
                behandlingsutfall = tidligere.behandlingsutfall?.toDto(),
            ),
            sisteVedtak = if (tidligere.behandlingsutfall?.resultat?.harVedtak == true) {
                null
            } else {
                tidligereSøknader.sisteVedtakSomDekker(aktivitetId, Periode(fra = fra, til = til))
                    ?.let { søknad -> søknad.behandlingsutfall?.toDto()?.let { SisteVedtakDto(søknadMottatt = søknad.mottatt, behandlingsutfall = it) } }
            },
        )
    }

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
 * Tiltaket i en tidligere søknad, med arrangør, type og navn fra [deltakelse] slik den er nå.
 * Søkere med adressebeskyttelse skal ikke få arrangørnavnet (se [toTiltakDto]).
 * Det gjelder også en søknad sendt inn før adressebeskyttelsen kom, der arrangørnavnet ligger lagret i klartekst.
 */
private fun Tiltak.medBeskrivelseFra(deltakelse: TiltaksdeltakelseDto) = Tiltak(
    aktivitetId = aktivitetId,
    periode = periode,
    arenaRegistrertPeriode = arenaRegistrertPeriode,
    arrangør = deltakelse.arrangør,
    type = deltakelse.type.name,
    typeNavn = deltakelse.typeNavn,
    gjennomforingId = deltakelse.gjennomforingId,
    visningsnavn = deltakelse.visningsnavn,
)

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
        tidligereSøknad = null,
        sisteVedtak = null,
    )
}

/**
 * Arena-koden for tiltakstypen.
 *
 * Domenetypen er vår egen klassifisering, mens `type` på wiren har alltid vært Arena-koden.
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
