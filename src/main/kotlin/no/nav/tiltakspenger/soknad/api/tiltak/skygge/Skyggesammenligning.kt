package no.nav.tiltakspenger.soknad.api.tiltak.skygge

import no.nav.tiltakspenger.libs.tiltak.TiltakshistorikkDTO
import no.nav.tiltakspenger.libs.tiltak.toTiltakstypeSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Kildestatus
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltaksdeltakelse
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakshistorikk
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakskilde
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.UkjentKildeverdi
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.somKildenTilsierManKanSøkePå
import no.nav.tiltakspenger.soknad.api.tiltak.erInnenforRelevantTidsrom
import java.time.LocalDate

/**
 * Utfallet av én sammenligning mellom deltakelsene søknaden viser i dag og uttrekket fra tiltaksdeltakelse-modulen.
 *
 * Ren data uten logging og uten tellere, slik at hele klassifiseringen kan testes uten fakes.
 * [ukjenteKildeverdier] og [manglendeKilder] er ikke uenighet mellom de to veiene, men funn ny vei bærer og gammel vei aldri kunne fortelle oss — de teller derfor ikke som avvik.
 */
data class Skyggeutfall(
    val antallFelles: Int,
    val feltavvik: List<Feltavvik>,
    val kunIGammel: List<KunIGammel>,
    val kunINy: List<KunINy>,
    val ukjenteKildeverdier: List<UkjentKildeverdi>,
    val manglendeKilder: Set<Tiltakskilde>,
) {
    val harAvvik: Boolean get() = feltavvik.isNotEmpty() || kunIGammel.isNotEmpty() || kunINy.isNotEmpty()
}

/**
 * Ett felt som ikke stemmer overens på en deltakelse begge veier fant.
 *
 * [gammel] og [ny] er verdiene slik de sto, og hører kun i sikkerlogg: tittel og arrangørnavn peker ut hvor personen møter opp.
 * [id] er kildesystemets id, som er personhenførbar i kontekst og derfor hører samme sted.
 */
data class Feltavvik(
    val id: String,
    val felt: String,
    val gammel: String?,
    val ny: String?,
)

/**
 * En deltakelse søknaden viser i dag, som uttrekket i ny vei ikke tar med.
 * Dette er den alvorligste bøtta: her mister bruker noe det går an å søke på i dag.
 */
data class KunIGammel(
    val id: String,
    val grunn: String,
)

/**
 * En deltakelse uttrekket i ny vei tar med, men som søknaden ikke viser i dag.
 * [kildestatus] er kilden og kontraktens statuskode, som er det som forklarer hvorfor gammel vei silte den bort.
 */
data class KunINy(
    val id: String,
    val kildestatus: String,
)

/**
 * Hvorfor en deltakelse gammel vei viser ikke er med i uttrekket fra ny vei.
 * Verdiene brukes som metrikk-labels og er derfor korte og stabile — begrunnelsene til visning bor i `Søkbarhet` i libs.
 */
object Fraværsgrunn {
    /** Raden finnes ikke i det hele tatt i svaret ny vei fikk. */
    const val MANGLER_I_NY = "mangler_i_ny"

    /** Datoene fra kilden henger ikke sammen, så raden ble `Ugyldig` og når aldri søknadsguarden. */
    const val UGYLDIGE_DATOER = "ugyldige_datoer"

    /** Tiltakskoden er ikke i tabellene våre. */
    const val UKJENT_TILTAKSKODE = "ukjent_tiltakskode"

    /** Tiltakskoden er kjent, men gir ikke rett til tiltakspenger. */
    const val TILTAKSTYPE_UTEN_RETT = "tiltakstype_uten_rett"

    /** Statuskoden fra kilden er ikke mappet ennå, og kan derfor ikke tolkes. */
    const val UKJENT_KILDESTATUS = "ukjent_kildestatus"

    /** Statusen er kjent, men gir ikke rett til å søke — her havner Arenas `IKKE_MOTT`, som er et kjent avvik som venter på fag. */
    const val STATUS_UTEN_RETT = "status_uten_rett"

    /** Raden passerer søknadsguarden, men faller utenfor tidsrommet — da har de to veiene ulike datoer, og det er i seg selv funnet. */
    const val UTENFOR_TIDSROM = "utenfor_tidsrom"
}

/**
 * Sammenligner deltakelsene søknaden viser i dag mot uttrekket fra tiltaksdeltakelse-modulen.
 *
 * Begge sider siles gjennom det samme tidsromsfilteret og matches på kildesystemets id, som er den samme strengen begge veier: `TA<arenaId>` for Arena, kildens UUID for Komet og Team Tiltak.
 * Sammenligningen er asymmetrisk med vilje — de to veiene skal ikke være like, de skal være forklarlig ulike — og hver rad som bare finnes én av stedene får en grunn den kan telles på.
 *
 * @param gammel lista `/tokenx/tiltakshistorikk` ga oss, som tiltak-appen allerede har silt til det bruker kan søke på.
 * @param ny hele hentingen fra ny vei; uttrekket gjøres her, og resten brukes til å forklare radene som mangler.
 * @param iDato datoen både søknadsguarden og tidsromsfilteret vurderes på.
 */
fun sammenlignSkygge(
    gammel: List<TiltakshistorikkDTO>,
    ny: Tiltakshistorikk,
    iDato: LocalDate,
): Skyggeutfall {
    val gammelPerId = gammel
        .filter { erInnenforRelevantTidsrom(fra = it.deltakelseFom, til = it.deltakelseTom, iDag = iDato) }
        .associateBy { it.id }

    val nyPerId = ny.deltakelser.somKildenTilsierManKanSøkePå(iDato).deltakelser
        .filter { erInnenforRelevantTidsrom(fra = it.fraOgMed, til = it.tilOgMed, iDag = iDato) }
        .associateBy { it.id.verdi }

    // Hele svaret, ikke bare uttrekket: en rad som mangler i uttrekket har som regel en forklaring blant radene som ble silt bort.
    val heleSvaretPerId = ny.deltakelser.deltakelser.associateBy { it.id.verdi }

    val felles = gammelPerId.keys intersect nyPerId.keys

    return Skyggeutfall(
        antallFelles = felles.size,
        feltavvik = felles.flatMap { id -> feltavvik(id, gammelPerId.getValue(id), nyPerId.getValue(id)) },
        kunIGammel = (gammelPerId.keys - nyPerId.keys).map { id ->
            KunIGammel(id = id, grunn = fraværsgrunn(heleSvaretPerId[id], iDato))
        },
        kunINy = (nyPerId.keys - gammelPerId.keys).map { id ->
            val status = nyPerId.getValue(id).kildestatus
            KunINy(id = id, kildestatus = "${status.kilde}:${status.kodeIKontrakten}")
        },
        ukjenteKildeverdier = ny.ukjenteKildeverdier,
        manglendeKilder = ny.meldinger.manglendeKilder,
    )
}

/**
 * Feltene søknaden faktisk bærer videre, og som derfor ender i den innsendte søknaden.
 * Alt annet i de to modellene er utenfor det denne skyggen skal svare på — feltparitet for saksbehandling måles i sin egen skygge i saksbehandling-api.
 */
private fun feltavvik(
    id: String,
    gammel: TiltakshistorikkDTO,
    ny: Tiltaksdeltakelse.GirRett,
): List<Feltavvik> = buildList {
    leggTil(id, "fraOgMed", gammel.deltakelseFom?.toString(), ny.fraOgMed?.toString())
    leggTil(id, "tilOgMed", gammel.deltakelseTom?.toString(), ny.tilOgMed?.toString())
    leggTil(id, "tiltakstype", gammel.gjennomforing.arenaKode.toTiltakstypeSomGirRett().getOrNull()?.name, ny.tiltakstype.name)
    leggTil(id, "tiltakstypenavn", gammel.gjennomforing.typeNavn, ny.tiltakstypenavn)
    leggTil(id, "tittel", gammel.gjennomforing.visningsnavn.ifBlank { null }, ny.tittel?.verdi)
    leggTil(id, "arrangørnavn", gammel.gjennomforing.arrangornavn?.ifBlank { null }, (ny.arrangør.hovedenhet ?: ny.arrangør.underenhet)?.verdi)
    leggTil(id, "dagerPerUke", gammel.antallDagerPerUke?.toString(), ny.omfang.dagerPerUke?.toString())
    leggTil(id, "deltakelsesprosent", gammel.deltakelseProsent?.toString(), ny.omfang.deltakelsesprosent?.toString())
    // Gjennomførings-id sammenlignes kun når gammel vei faktisk bar en.
    // Dagens mapper setter tom streng for Arena og Team Tiltak, mens ny vei bærer Arenas gjennomføring — et tilsiktet avvik som ellers ville truffet hver eneste Arena-rad og druknet resten av signalet.
    val gammelGjennomføringId = gammel.gjennomforing.id.ifBlank { null }
    if (gammelGjennomføringId != null) {
        leggTil(id, "gjennomføringId", gammelGjennomføringId, ny.gjennomføringId?.verdi)
    }
}

private fun MutableList<Feltavvik>.leggTil(id: String, felt: String, gammel: String?, ny: String?) {
    if (gammel != ny) {
        add(Feltavvik(id = id, felt = felt, gammel = gammel, ny = ny))
    }
}

/**
 * Klassifiserer hvorfor en rad gammel vei viser ikke er med i uttrekket.
 * Kaskaden speiler `søkbarhet` i libs: datakvalitet slår ut først, så tiltakstypeaksen, så statusaksen — og til slutt står bare tidsrommet igjen.
 */
private fun fraværsgrunn(ny: Tiltaksdeltakelse?, iDato: LocalDate): String {
    if (ny == null) return Fraværsgrunn.MANGLER_I_NY
    return when (ny) {
        is Tiltaksdeltakelse.Ugyldig -> Fraværsgrunn.UGYLDIGE_DATOER

        is Tiltaksdeltakelse.UkjentTiltakstype -> Fraværsgrunn.UKJENT_TILTAKSKODE

        is Tiltaksdeltakelse.GirIkkeRett -> Fraværsgrunn.TILTAKSTYPE_UTEN_RETT

        is Tiltaksdeltakelse.GirRett.MedPeriode,
        is Tiltaksdeltakelse.GirRett.UtenPeriode,
        -> fraværsgrunnForRadSomGirRett(ny, iDato)
    }
}

private fun fraværsgrunnForRadSomGirRett(ny: Tiltaksdeltakelse, iDato: LocalDate): String {
    val status = ny.kildestatus
    if (status !is Kildestatus.Kjent) return Fraværsgrunn.UKJENT_KILDESTATUS
    if (!status.deltakerstatus(fraOgMed = ny.fraOgMed, påDato = iDato).girRettTilÅSøke) return Fraværsgrunn.STATUS_UTEN_RETT
    return Fraværsgrunn.UTENFOR_TIDSROM
}
