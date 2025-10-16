package no.nav.tiltakspenger.soknad.api.soknad

data class Geografi(
    val noder: Map<String, Hierarkinode>,
)

data class Hierarkinode(
    val kode: String,
    val termer: Map<String, String>,
    val undernoder: Map<String, Hierarkinode>?,
)
