package no.nav.tiltakspengesoknad.api.pdl

import java.time.LocalDateTime

data class FolkeregisterMetadata(
    val aarsak: String?,
    val ajourholdstidspunkt: LocalDateTime?,
    val gyldighetstidspunkt: LocalDateTime?,
    val kilde: String?,
    val opphoerstidspunkt: LocalDateTime?,
    val sekvens: Int?,
)
