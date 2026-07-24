package no.nav.tiltakspenger.soknad.api.metrics

import io.kotest.matchers.shouldBe
import io.prometheus.client.CollectorRegistry
import org.junit.jupiter.api.Test

class MetricsCollectorTest {
    @Test
    fun `registrerer tellere og summary som kan brukes`() {
        // Eget register per test, ikke det globale CollectorRegistry.defaultRegistry — ingen delt, muterende tilstand mellom tester.
        val metricsCollector = MetricsCollector(CollectorRegistry())

        metricsCollector.antallSøknaderMottattCounter.inc()
        metricsCollector.antallUgyldigeSøknaderCounter.inc()
        metricsCollector.antallFeiledeInnsendingerCounter.inc()
        metricsCollector.antallFeilVedHentPersonaliaCounter.inc()
        metricsCollector.antallFeilVedHentTiltakCounter.inc()
        metricsCollector.søknadsmottakLatencySeconds.observe(0.5)

        metricsCollector.antallSøknaderMottattCounter.get() shouldBe 1.0
        metricsCollector.søknadsmottakLatencySeconds.get().count shouldBe 1.0
    }
}
