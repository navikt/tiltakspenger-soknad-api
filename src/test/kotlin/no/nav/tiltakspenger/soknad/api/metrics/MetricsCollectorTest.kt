package no.nav.tiltakspenger.soknad.api.metrics

import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test

class MetricsCollectorTest {
    @Test
    fun `registrerer tellere og en timer som kan brukes`() {
        // Eget register per test — ingen delt, muterende tilstand mellom tester.
        val metricsCollector = MetricsCollector(SimpleMeterRegistry())

        metricsCollector.antallSøknaderMottattCounter.increment()
        metricsCollector.antallUgyldigeSøknaderCounter.increment()
        metricsCollector.antallFeiledeInnsendingerCounter.increment()
        metricsCollector.antallFeilVedHentPersonaliaCounter.increment()
        metricsCollector.antallFeilVedHentTiltakCounter.increment()
        metricsCollector.startSøknadsmottak().stop(metricsCollector.søknadsmottakLatency)

        metricsCollector.antallSøknaderMottattCounter.count() shouldBe 1.0
        metricsCollector.søknadsmottakLatency.count() shouldBe 1L
    }
}
