package no.nav.tiltakspenger.soknad.api

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.libs.texas.client.TexasClient
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.soknad.api.testutils.FakeSøknadRepo
import org.junit.jupiter.api.Test

/**
 * Wiringen er ikke lenger gjemt i det Kover-ekskluderte oppstartsskriptet, så den kan bygges og sjekkes her.
 * Konteksten gjør ingen I/O ved konstruksjon: datasourcen bygges i `start()` og sendes inn ferdig, og resten leses fra [Configuration], som utenfor Nais faller tilbake på lokale verdier.
 */
class ApplicationContextTest {
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
            context.personKlient
            context.pdlIdentklient
            context.tiltakshistorikkKlient
            context.tiltakshistorikkHenter
            context.avKlient
            context.pdfGenerator
            context.journalpostKlient
            context.saksbehandlingKlient
            context.pdfService
            context.dokarkivService
            context.journalforingService
            context.pdlService
            context.tiltakService
            context.avService
            context.nySøknadService
            context.identhendelseService
            // Konstruerer en ekte KafkaConsumer mot lokal KafkaConfig; den kobler ikke til før run() kalles.
            context.identhendelseConsumer
            context.søknadJobbService
        }
    }

    @Test
    fun `hver avhengighet bygges bare én gang`() {
        val context = context()

        // Klientene holder både HTTP-klient og cache, så en ny instans per oppslag ville vært en reell feil i drift.
        context.texasClient shouldBeSameInstanceAs context.texasClient
        context.personKlient shouldBeSameInstanceAs context.personKlient
        context.pdlIdentklient shouldBeSameInstanceAs context.pdlIdentklient
        context.tiltakshistorikkKlient shouldBeSameInstanceAs context.tiltakshistorikkKlient
        context.pdlService shouldBeSameInstanceAs context.pdlService
        context.søknadJobbService shouldBeSameInstanceAs context.søknadJobbService
        context.metricsCollector shouldBeSameInstanceAs context.metricsCollector
    }

    /**
     * Regresjonsvern: scopene våre kommer fra nais-manifestet som `cluster:namespace:app`, og Azure AD svarer `invalid_scope` (AADSTS1002012) på alt annet enn `api://…/.default`.
     * Da omskrivingen ble skrudd av her, falt PDL-oppslag, journalføring og saksnummer-jobben i produksjon uten at noen test merket det.
     */
    @Test
    fun `systemtokens ber Azure AD om target på api-formatet`() = runTest {
        val transport = FakeHttpTransport().apply {
            leggIKøJson(json = """{"access_token": "system-token", "expires_in": 3600}""")
        }
        val context = object : ApplicationContext(
            clock = fixedClock,
            søknadRepo = FakeSøknadRepo(),
            collectorRegistry = CollectorRegistry(),
        ) {
            override val texasClient: TexasClient = TexasHttpClient(
                introspectionUrl = "http://texas/api/v1/introspect",
                tokenUrl = "http://texas/api/v1/token",
                tokenExchangeUrl = "http://texas/api/v1/token/exchange",
                clock = fixedClock,
                transport = transport,
            )
        }

        context.systemTokenProvider("prod-fss:pdl:pdl-api").hentToken(skipCache = false)

        val requestBody = deserialize<Map<String, Any?>>(transport.mottatteKall.single().bodyTekst)
        requestBody["target"] shouldBe "api://prod-fss.pdl.pdl-api/.default"
        requestBody["identity_provider"] shouldBe "azuread"
    }
}
