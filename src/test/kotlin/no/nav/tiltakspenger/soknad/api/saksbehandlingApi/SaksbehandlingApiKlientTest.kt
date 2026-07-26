package no.nav.tiltakspenger.soknad.api.saksbehandlingApi

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.soknad.api.soknad.validering.søknad
import no.nav.tiltakspenger.soknad.api.testutils.testTokenProvider
import org.junit.jupiter.api.Test

internal class SaksbehandlingApiKlientTest {
    private val fnr = Fnr.fromString("12345678910")
    private val correlationId = CorrelationId("correlation-id")

    private fun klient(baseUrl: String, transport: HttpTransport? = null) = if (transport == null) {
        SaksbehandlingApiKlient(baseUrl = baseUrl, clock = fixedClock, authTokenProvider = testTokenProvider)
    } else {
        SaksbehandlingApiKlient(baseUrl = baseUrl, clock = fixedClock, authTokenProvider = testTokenProvider, transport = transport)
    }

    @Test
    fun `henter saksnummer med default HttpKlient-oppsett`() {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                post(urlEqualTo("/saksnummer")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""{"saksnummer":"202501011001"}"""),
                ),
            )

            runTest {
                klient(wiremock.baseUrl()).hentEllerOpprettSaksnummer(fnr, correlationId).getOrFail() shouldBe "202501011001"
            }
        }
    }

    @Test
    fun `sender fnr og Nav-Call-Id ved henting av saksnummer`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"saksnummer":"202501011001"}""")

        klient("http://sb-api", transport).hentEllerOpprettSaksnummer(fnr, correlationId).getOrFail()

        val kall = transport.mottatteKall.single()
        kall.uri.toString() shouldBe "http://sb-api/saksnummer"
        kall.bodyTekst shouldBe """{"fnr":"12345678910"}"""
        kall.request.headers().firstValue("Nav-Call-Id").get() shouldBe "correlation-id"
        kall.request.headers().firstValue("Authorization").get() shouldBe "Bearer test-token"
    }

    @Test
    fun `sendSøknad poster søknaden og bryr seg ikke om responsbodyen`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(200, body = "hva som helst", contentType = "text/plain")

        klient("http://sb-api", transport).sendSøknad(søknadDTO(), correlationId).getOrFail() shouldBe Unit

        val kall = transport.mottatteKall.single()
        kall.uri.toString() shouldBe "http://sb-api/soknad"
        kall.request.headers().firstValue("Nav-Call-Id").get() shouldBe "correlation-id"
    }

    @Test
    fun `godtar kun 200, ikke andre 2xx`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(202, body = "akseptert")

        val feil = klient("http://sb-api", transport).sendSøknad(søknadDTO(), correlationId).leftOrNull()!!

        feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 202
    }

    @Test
    fun `feilstatus gir Left med lesbar body`() = runTest {
        val transport = FakeHttpTransport()
        // 500 er retryable, så alle fire forsøkene må legges i kø.
        repeat(4) { transport.leggIKøStatus(500, body = "noe gikk galt") }

        val feil = klient("http://sb-api", transport).hentEllerOpprettSaksnummer(fnr, correlationId).leftOrNull()!!

        feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>().body shouldContain "noe gikk galt"
    }

    @Test
    fun `retryer serverfeil fire ganger totalt, som den gamle ktor-klienten`() = runTest {
        val transport = FakeHttpTransport()
        repeat(4) { transport.leggIKøStatus(503, body = "nede") }

        klient("http://sb-api", transport).hentEllerOpprettSaksnummer(fnr, correlationId).leftOrNull()!!

        transport.mottatteKall.size shouldBe 4
    }

    private fun søknadDTO() = søknadMapper(
        søknad = søknad(),
        jounalpostId = JournalpostId("123456789"),
        saksnummer = "202501011001",
    )

    @Test
    fun `FnrDTO maskerer fnr i toString slik at det ikke lekker til logg`() {
        FnrDTO("12345678910").toString() shouldBe "FnrDTO(fnr=*****)"
        FnrDTO("12345678910").toString() shouldNotContain "12345678910"
    }
}
