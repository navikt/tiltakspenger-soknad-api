package no.nav.tiltakspenger.soknad.api

const val SØKNAD_PATH = "/soknad"
const val TILTAK_PATH = "/tiltak"
const val PERSONALIA_PATH = "/personalia"

/** MDC-nøkkelen correlation id-en logges under, både i Ktor-oppsettet og i bakgrunnsprosessene. */
const val CALL_ID_MDC_KEY = "call-id"
