package no.nav.tiltakspenger.soknad.api

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.prometheus.client.CollectorRegistry
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.testutils.FakeSøknadRepo
import org.junit.jupiter.api.Test

/**
 * Wiringen er ikke lenger gjemt i det Kover-ekskluderte oppstartsskriptet, så den kan bygges og sjekkes her.
 * Konteksten gjør ingen I/O ved konstruksjon: datasourcen bygges i `start()` og sendes inn ferdig, og resten leses fra [Configuration], som utenfor Nais faller tilbake på lokale verdier.
 */
internal class ApplicationContextTest {
    private fun context() = ApplicationContext(
        clock = fixedClock,
        søknadRepo = FakeSøknadRepo(),
        collectorRegistry = CollectorRegistry(),
    )

    @Test
    fun `hele avhengighetsgrafen kan bygges fra Configuration`() {
        val context = context()

        shouldNotThrowAny {
            context.sikkerlogg
            context.texasClient
            context.metricsCollector
            context.pdlClient
            context.tiltakspengerTiltakClient
            context.clamAvClient
            context.pdfClient
            context.dokarkivClient
            context.saksbehandlingApiKlient
            context.pdfService
            context.dokarkivService
            context.journalforingService
            context.pdlService
            context.tiltakService
            context.avService
            context.nySøknadService
            context.identhendelseService
            // Konstruerer en ekte KafkaConsumer mot LocalKafkaConfig; den kobler ikke til før run() kalles.
            context.identhendelseConsumer
            context.søknadJobbService
        }
    }

    @Test
    fun `hver avhengighet bygges bare én gang`() {
        val context = context()

        // Klientene holder både HTTP-klient og cache, så en ny instans per oppslag ville vært en reell feil i drift.
        context.texasClient shouldBeSameInstanceAs context.texasClient
        context.pdlClient shouldBeSameInstanceAs context.pdlClient
        context.tiltakspengerTiltakClient shouldBeSameInstanceAs context.tiltakspengerTiltakClient
        context.pdlService shouldBeSameInstanceAs context.pdlService
        context.søknadJobbService shouldBeSameInstanceAs context.søknadJobbService
        context.metricsCollector shouldBeSameInstanceAs context.metricsCollector
    }
}
