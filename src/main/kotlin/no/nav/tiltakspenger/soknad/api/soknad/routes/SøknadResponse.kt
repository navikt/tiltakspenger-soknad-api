package no.nav.tiltakspenger.soknad.api.soknad.routes

import java.time.LocalDateTime

data class SøknadResponse(
    val innsendingTidspunkt: LocalDateTime,
)

/**
 * Svaret med status 409 når brukeren sender en søknad med de samme svarene som en søknad vi allerede har mottatt for samme tiltaksdeltakelse og periode, og som ikke er ferdig behandlet.
 * Frontenden kjenner igjen svaret på [kode] og viser når vi mottok den tidligere søknaden.
 */
data class SøknadUtenEndringerResponse(
    val mottatt: LocalDateTime,
) {
    val kode: String = "SØKNAD_UTEN_ENDRINGER"
    val melding: String = "Søknaden har de samme svarene som en søknad vi allerede har mottatt for denne tiltaksdeltakelsen og perioden, og som ikke er ferdig behandlet."
}
