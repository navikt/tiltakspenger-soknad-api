package no.nav.tiltakspenger.soknad.api.tiltak

import mu.KotlinLogging
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.TiltakDTO
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.TiltakType
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
) {
    private val log = KotlinLogging.logger {}

    fun erInnenforRelevantTidsrom(): Boolean {
        if (arenaRegistrertPeriode.fra == null || arenaRegistrertPeriode.til == null) {
            log.info { "filtrere bort tiltak med id $aktivitetId pga null i periode $arenaRegistrertPeriode" }
            return false
        }

        val datoFor6MånederSiden = LocalDate.now().minusMonths(6)
        val dato2MånederFrem = LocalDate.now().plusMonths(2)

        return arenaRegistrertPeriode.fra.isBefore(dato2MånederFrem) && arenaRegistrertPeriode.til.isAfter(datoFor6MånederSiden)
    }
}

fun List<TiltakDTO>.toTiltakDto(maskerArrangørnavn: Boolean): List<TiltaksdeltakelseDto> {
    return this.map {
        TiltaksdeltakelseDto(
            aktivitetId = it.id,
            type = it.gjennomforing.arenaKode,
            typeNavn = it.gjennomforing.typeNavn,
            arenaRegistrertPeriode = Deltakelsesperiode(
                fra = it.deltakelseFom,
                til = it.deltakelseTom,
            ),
            arrangør = if (maskerArrangørnavn) "" else it.gjennomforing.arrangørnavn,
            // status = ArenaTiltaksaktivitetResponsDTO.DeltakerStatusType.valueOf(it.status.name),
        )
    }
}
