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

    // Tellerne under hører til skyggekjøringen av tiltaksdeltakelse-modulen og dør med den.
    // De er de eneste tellerne her med labels, fordi et avvik uten klassifisering ikke er til å handle på.

    val tiltaksdeltakelseSkyggeKjøringer: Counter = Counter.build()
        .name("tiltakspenger_soknad_tiltaksdeltakelse_skygge_kjoeringer")
        .namespace(namespace)
        .labelNames("utfall")
        .help("Antall skyggekjøringer av tiltaksdeltakelse-modulen, fordelt på utfall")
        .register(registry)

    val tiltaksdeltakelseSkyggeFeltavvik: Counter = Counter.build()
        .name("tiltakspenger_soknad_tiltaksdeltakelse_skygge_feltavvik")
        .namespace(namespace)
        .labelNames("felt")
        .help("Felter som er ulike på deltakelser begge veier fant")
        .register(registry)

    val tiltaksdeltakelseSkyggeKunIGammel: Counter = Counter.build()
        .name("tiltakspenger_soknad_tiltaksdeltakelse_skygge_kun_i_gammel")
        .namespace(namespace)
        .labelNames("grunn")
        .help("Deltakelser søknaden viser i dag som uttrekket i ny vei ikke tar med")
        .register(registry)

    val tiltaksdeltakelseSkyggeKunINy: Counter = Counter.build()
        .name("tiltakspenger_soknad_tiltaksdeltakelse_skygge_kun_i_ny")
        .namespace(namespace)
        .labelNames("kildestatus")
        .help("Deltakelser uttrekket i ny vei tar med som søknaden ikke viser i dag")
        .register(registry)

    val tiltaksdeltakelseSkyggeSammenlignedeDeltakelser: Counter = Counter.build()
        .name("tiltakspenger_soknad_tiltaksdeltakelse_skygge_sammenlignede_deltakelser")
        .namespace(namespace)
        .help("Deltakelser begge veier fant og faktisk sammenlignet — hvor mye vi har verifisert, ikke bare hvor mange oppslag som er gjort")
        .register(registry)

    val tiltaksdeltakelseSkyggeIdentfallback: Counter = Counter.build()
        .name("tiltakspenger_soknad_tiltaksdeltakelse_skygge_identfallback")
        .namespace(namespace)
        .labelNames("grunn")
        .help("Oppslag der PDL ikke ga brukbare identer, så historikken ble hentet på innsendt fnr alene")
        .register(registry)

    val tiltaksdeltakelseSkyggeSøkbarVedUnntak: Counter = Counter.build()
        .name("tiltakspenger_soknad_tiltaksdeltakelse_skygge_soekbar_ved_unntak")
        .namespace(namespace)
        .labelNames("kildestatus")
        .help("Deltakelser som bare er søkbare fordi et unntak i Søkbarhet slipper dem gjennom — i dag Arenas «ikke møtt»")
        .register(registry)

    val tiltaksdeltakelseSkyggeUkjentKildeverdi: Counter = Counter.build()
        .name("tiltakspenger_soknad_tiltaksdeltakelse_skygge_ukjent_kildeverdi")
        .namespace(namespace)
        .labelNames("hva")
        .help("Kildeverdier ny vei ikke kjente igjen — statuser, tiltakskoder, Komet-årsaker, meldinger og deltakelsesformer")
        .register(registry)

    val tiltaksdeltakelseSkyggeManglendeKilde: Counter = Counter.build()
        .name("tiltakspenger_soknad_tiltaksdeltakelse_skygge_manglende_kilde")
        .namespace(namespace)
        .labelNames("kilde")
        .help("Kilder tiltakshistorikk meldte at svaret er ufullstendig for")
        .register(registry)
}
