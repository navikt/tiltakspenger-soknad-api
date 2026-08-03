package no.nav.tiltakspenger.soknad.api.tiltak

import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.TiltakTypeDTO
import no.nav.tiltakspenger.libs.tiltak.TiltakshistorikkDTO
import java.time.Clock
import java.time.LocalDate

data class Deltakelsesperiode(
    val fra: LocalDate?,
    val til: LocalDate?,
)

data class TiltaksdeltakelseDto(
    val aktivitetId: String,
    val type: TiltakTypeDTO,
    val typeNavn: String,
    val arenaRegistrertPeriode: Deltakelsesperiode,
    val arrangør: String,
    val gjennomforingId: String,
    val visningsnavn: String?,
) {
    fun erInnenforRelevantTidsrom(clock: Clock): Boolean =
        erInnenforRelevantTidsrom(
            fra = arenaRegistrertPeriode.fra,
            til = arenaRegistrertPeriode.til,
            iDag = LocalDate.now(clock),
        )
}

/**
 * Tidsrommet søknaden viser tiltaksdeltakelser fra: seks måneder tilbake til to måneder fram.
 * En deltakelse uten startdato slipper alltid gjennom — vi vet ikke når den hører hjemme, og skjuler den derfor ikke.
 *
 * Regelen ligger som fri funksjon fordi skyggekjøringen må sile både gammel og ny vei gjennom nøyaktig det samme predikatet.
 * Filtrerte vi bare den ene siden, ville hele forskjellen mellom dem vært dette filteret.
 */
fun erInnenforRelevantTidsrom(fra: LocalDate?, til: LocalDate?, iDag: LocalDate): Boolean {
    val datoFor6MånederSiden = iDag.minusMonths(6)
    val dato2MånederFrem = iDag.plusMonths(2)

    return if (fra == null) {
        true
    } else if (til == null) {
        fra.isBefore(dato2MånederFrem) && fra.isAfter(datoFor6MånederSiden)
    } else {
        fra.isBefore(dato2MånederFrem) && til.isAfter(datoFor6MånederSiden)
    }
}

fun List<TiltakshistorikkDTO>.toTiltakDto(maskerArrangørnavn: Boolean): List<TiltaksdeltakelseDto> {
    return this.map {
        TiltaksdeltakelseDto(
            aktivitetId = it.id,
            type = it.gjennomforing.arenaKode,
            typeNavn = it.gjennomforing.typeNavn,
            arenaRegistrertPeriode = Deltakelsesperiode(
                fra = it.deltakelseFom,
                til = it.deltakelseTom,
            ),
            arrangør = if (maskerArrangørnavn) "" else it.gjennomforing.arrangornavn ?: "Ukjent",
            gjennomforingId = it.gjennomforing.id,
            visningsnavn = if (maskerArrangørnavn) it.gjennomforing.typeNavn else it.gjennomforing.visningsnavn,
        )
    }
}
