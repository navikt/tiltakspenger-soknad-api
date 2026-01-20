package no.nav.tiltakspenger.soknad.api.tiltak

import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.TiltakType
import no.nav.tiltakspenger.libs.tiltak.TiltakshistorikkDTO
import java.time.LocalDate

data class Deltakelsesperiode(
    val fra: LocalDate?,
    val til: LocalDate?,
)

data class TiltaksdeltakelseDto(
    val aktivitetId: String,
    val type: TiltakType,
    val typeNavn: String,
    val arenaRegistrertPeriode: Deltakelsesperiode,
    val arrangør: String,
    val gjennomforingId: String,
    val visningsnavn: String?,
) {
    fun erInnenforRelevantTidsrom(): Boolean {
        val datoFor6MånederSiden = LocalDate.now().minusMonths(6)
        val dato2MånederFrem = LocalDate.now().plusMonths(2)

        return if (arenaRegistrertPeriode.fra == null) {
            true
        } else if (arenaRegistrertPeriode.til == null) {
            arenaRegistrertPeriode.fra.isBefore(dato2MånederFrem) && arenaRegistrertPeriode.fra.isAfter(datoFor6MånederSiden)
        } else {
            arenaRegistrertPeriode.fra.isBefore(dato2MånederFrem) && arenaRegistrertPeriode.til.isAfter(datoFor6MånederSiden)
        }
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
