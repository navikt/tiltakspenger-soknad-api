package no.nav.tiltakspenger.soknad.api.metrics

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Summary

/**
 * @param registry Registeret metrikkene registreres i.
 * I drift sendes det globale [CollectorRegistry.defaultRegistry] inn (det `/metrics` scraper).
 * Tester sender inn et eget register for å unngå å dele global, muterende tilstand.
 */
class MetricsCollector(
    registry: CollectorRegistry,
) {
    private val namespace = "tpts"

    val antallSøknaderMottattCounter: Counter = Counter.build()
        .name("tiltakspenger_soknad_antall_soknader_mottatt")
        .namespace(namespace)
        .help("Antall søknader mottatt")
        .register(registry)

    val antallUgyldigeSøknaderCounter: Counter = Counter.build()
        .name("tiltakspenger_soknad_antall_ugyldige_soknader")
        .namespace(namespace)
        .help("Antall ugyldige søknader forsøkt sendt inn")
        .register(registry)

    val antallFeiledeInnsendingerCounter: Counter = Counter.build()
        .name("tiltakspenger_soknad_antall_soknader_feilet")
        .namespace(namespace)
        .help("Antall feilede søknadsinnsendinger")
        .register(registry)

    val antallFeilVedHentPersonaliaCounter: Counter = Counter.build()
        .name("tiltakspenger_soknad_antall_feil_ved_hent_personalia")
        .namespace(namespace)
        .help("Antall ganger personalia-kall har feilet")
        .register(registry)

    val antallFeilVedHentTiltakCounter: Counter = Counter.build()
        .name("tiltakspenger_soknad_antall_feil_ved_hent_tiltak")
        .namespace(namespace)
        .help("Antall ganger tiltak-kall har feilet")
        .register(registry)

    val søknadsmottakLatencySeconds: Summary = Summary.build()
        .name("tiltakspenger_soknad_soknadsmottak_latency_seconds")
        .namespace(namespace)
        .help("Hvor lang tid det tar å prosessere en søknad (i sekunder)")
        .register(registry)
}
