package no.nav.tiltakspenger.soknad.api.metrics

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import no.nav.tiltakspenger.soknad.api.testutils.medTestApplikasjon
import org.junit.jupiter.api.Test

/**
 * Pinner eksposisjonsnavnene appens egne målinger får på `/metrics`.
 *
 * Tre av tellerne er kontrakt: varselreglene i `.nais/alerts.yml` spør på nøyaktig disse navnene, og et navn som endrer seg gjør varselet stille i stedet for å feile.
 * Micrometer utleder navnene selv — punktum blir understrek, tellere får `_total`, og timeren får `_seconds` med `_count`, `_sum` og `_max` — så navnene må måles på den ekte eksposisjonen, ikke leses ut av koden.
 */
class MetricRoutesTest {
    @Test
    fun `metrics eksponerer appens egne målinger med navnene varslene spør på`() {
        medTestApplikasjon { tac ->
            tac.metricsCollector.antallSøknaderMottattCounter.increment()
            tac.metricsCollector.antallUgyldigeSøknaderCounter.increment()
            tac.metricsCollector.antallFeiledeInnsendingerCounter.increment()
            tac.metricsCollector.antallFeilVedHentPersonaliaCounter.increment()
            tac.metricsCollector.antallFeilVedHentTiltakCounter.increment()
            tac.metricsCollector.startSøknadsmottak().stop(tac.metricsCollector.søknadsmottakLatency)

            client.get("/metrics").apply {
                status shouldBe HttpStatusCode.OK
                val metrikker = bodyAsText()

                // Mellomrommet etter navnet pinner at navnet slutter der, både på HELP-linja og på selve målingen.
                metrikker shouldContain "tpts_tiltakspenger_soknad_antall_soknader_mottatt_total "
                metrikker shouldContain "tpts_tiltakspenger_soknad_antall_ugyldige_soknader_total "
                metrikker shouldContain "tpts_tiltakspenger_soknad_antall_soknader_feilet_total "
                metrikker shouldContain "tpts_tiltakspenger_soknad_antall_feil_ved_hent_personalia_total "
                metrikker shouldContain "tpts_tiltakspenger_soknad_antall_feil_ved_hent_tiltak_total "
                metrikker shouldContain "tpts_tiltakspenger_soknad_soknadsmottak_latency_seconds_count "
                metrikker shouldContain "tpts_tiltakspenger_soknad_soknadsmottak_latency_seconds_sum "
                // Ktor-metrikkene og JVM-binderne kommer fra MicrometerMetrics-pluginen, og ligger i det samme registeret.
                metrikker shouldContain "ktor_http_server_requests"
                metrikker shouldContain "jvm_memory_used_bytes"
            }
        }
    }
}
