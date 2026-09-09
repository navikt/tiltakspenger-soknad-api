package no.nav.tiltakspenger.soknad.api.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer

/**
 * Appens egne forretningsmålinger: fem tellere og tidsbruken på søknadsmottaket.
 *
 * Navnene her er skrevet med punktum, slik Micrometer vil ha dem, og oversettes til understrek i eksposisjonen.
 * Tellerne får i tillegg `_total`, og [søknadsmottakLatency] får `_seconds` med `_count`, `_sum` og `_max`.
 * Tre av tellerne er kontrakt mot varselreglene i `.nais/alerts.yml`, så de eksakte eksposisjonsnavnene pinnes av `MetricRoutesTest`.
 *
 * @param meterRegistry Registeret målingene registreres i, og som `/metrics` skraper.
 * Injiseres fra [no.nav.tiltakspenger.soknad.api.ApplicationContext] slik at appen har nøyaktig ett register.
 */
class MetricsCollector(
    private val meterRegistry: MeterRegistry,
) {
    val antallSøknaderMottattCounter: Counter = Counter
        .builder("tpts.tiltakspenger.soknad.antall.soknader.mottatt")
        .description("Antall søknader mottatt")
        .register(meterRegistry)

    val antallUgyldigeSøknaderCounter: Counter = Counter
        .builder("tpts.tiltakspenger.soknad.antall.ugyldige.soknader")
        .description("Antall ugyldige søknader forsøkt sendt inn")
        .register(meterRegistry)

    val antallFeiledeInnsendingerCounter: Counter = Counter
        .builder("tpts.tiltakspenger.soknad.antall.soknader.feilet")
        .description("Antall feilede søknadsinnsendinger")
        .register(meterRegistry)

    val antallFeilVedHentPersonaliaCounter: Counter = Counter
        .builder("tpts.tiltakspenger.soknad.antall.feil.ved.hent.personalia")
        .description("Antall ganger personalia-kall har feilet")
        .register(meterRegistry)

    val antallFeilVedHentTiltakCounter: Counter = Counter
        .builder("tpts.tiltakspenger.soknad.antall.feil.ved.hent.tiltak")
        .description("Antall ganger tiltak-kall har feilet")
        .register(meterRegistry)

    val søknadsmottakLatency: Timer = Timer
        .builder("tpts.tiltakspenger.soknad.soknadsmottak.latency")
        .description("Hvor lang tid det tar å prosessere en søknad (i sekunder)")
        .register(meterRegistry)

    /**
     * Starter en måling av søknadsmottaket.
     * Hver utgang av ruta stopper prøven mot [søknadsmottakLatency], slik at varigheten registreres uansett hvilket svar brukeren får.
     */
    fun startSøknadsmottak(): Timer.Sample = Timer.start(meterRegistry)
}
