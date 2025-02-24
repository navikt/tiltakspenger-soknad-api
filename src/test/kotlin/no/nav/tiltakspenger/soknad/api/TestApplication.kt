package no.nav.tiltakspenger.soknad.api

import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.mockk
import no.nav.tiltakspenger.soknad.api.antivirus.AvService
import no.nav.tiltakspenger.soknad.api.auth.texas.client.TexasClient
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.soknad.NySøknadService
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakService
import java.util.UUID.randomUUID

fun ApplicationTestBuilder.configureTestApplication(
    texasClient: TexasClient = mockk<TexasClient>(),
    pdlService: PdlService = mockk(),
    nySøknadService: NySøknadService = mockk(),
    tiltakService: TiltakService = mockk(),
    avService: AvService = mockk(),
    metricsCollector: MetricsCollector = mockk(relaxed = true),
) {
    application {
        install(CallId) {
            generate { randomUUID().toString() }
        }
        install(CallLogging) {
            callIdMdc("call-id")
        }
        setupRouting(
            texasClient = texasClient,
            pdlService = pdlService,
            tiltakService = tiltakService,
            avService = avService,
            metricsCollector = metricsCollector,
            nySøknadService = nySøknadService,
        )
        installJacksonFeature()
    }
}
