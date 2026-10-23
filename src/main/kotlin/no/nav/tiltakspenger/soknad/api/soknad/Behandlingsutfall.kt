package no.nav.tiltakspenger.soknad.api.soknad

import java.time.LocalDateTime

/**
 * Utfallet av saksbehandlingen av en søknad, slik saksbehandling-api melder det når vedtaket er iverksatt.
 * En søknad med et utfall er ferdig behandlet.
 *
 * @property vedtakId Vedtaket i saksbehandling-api som avgjorde søknaden.
 * @property vedtakstidspunkt Når vedtaket ble iverksatt.
 */
data class Behandlingsutfall(
    val resultat: Behandlingsresultat,
    val vedtakId: String,
    val vedtakstidspunkt: LocalDateTime,
)

enum class Behandlingsresultat {
    INNVILGELSE,
    AVSLAG,
}

/** Søknaden saksbehandling-api meldte utfall for, finnes ikke hos oss. */
data object SøknadFinnesIkke
