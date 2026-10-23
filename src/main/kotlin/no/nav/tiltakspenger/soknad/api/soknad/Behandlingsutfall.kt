package no.nav.tiltakspenger.soknad.api.soknad

import java.time.LocalDateTime

/**
 * Siste kjente utfall av saksbehandlingen av en søknad, slik saksbehandling-api melder det.
 * Saksbehandling-api melder når et vedtak er iverksatt, og når søknaden avbrytes eller gjenåpnes.
 * Vi beholder utfallet med nyest [tidspunkt].
 *
 * @property vedtakId Vedtaket i saksbehandling-api som avgjorde søknaden.
 * Satt for [Behandlingsresultat.INNVILGELSE] og [Behandlingsresultat.AVSLAG], og null for avbrudd og gjenåpning, som ikke har vedtak.
 * @property tidspunkt Når vedtaket ble opprettet, eller når søknaden ble avbrutt eller gjenåpnet.
 */
data class Behandlingsutfall(
    val resultat: Behandlingsresultat,
    val tidspunkt: LocalDateTime,
    val vedtakId: String?,
) {
    init {
        require((vedtakId != null) == resultat.harVedtak) {
            "Behandlingsutfall $resultat skal ${if (resultat.harVedtak) "" else "ikke "}ha vedtakId"
        }
    }
}

/**
 * @property harVedtak Om resultatet kommer fra et vedtak.
 * @property erFerdigBehandlet Om søknaden er ferdig behandlet, slik at bruker kan sende den samme søknaden på nytt.
 */
enum class Behandlingsresultat(val harVedtak: Boolean, val erFerdigBehandlet: Boolean) {
    INNVILGELSE(harVedtak = true, erFerdigBehandlet = true),
    AVSLAG(harVedtak = true, erFerdigBehandlet = true),

    /** Søknaden ble avbrutt uten vedtak. */
    AVBRUTT(harVedtak = false, erFerdigBehandlet = true),

    /** En avbrutt søknad er gjenåpnet og under behandling igjen. */
    GJENÅPNET(harVedtak = false, erFerdigBehandlet = false),
}

/** Søknaden saksbehandling-api meldte utfall for, finnes ikke hos oss. */
data object SøknadFinnesIkke
