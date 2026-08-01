package no.nav.tiltakspenger.soknad.api.tiltak

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.random
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import org.junit.jupiter.api.Test

class TiltakspengerTiltakClientTest {
    // TexasHttpClient er en final klasse som ikke kan byttes ut i libs, så OBO-vekslingen mockes; selve HTTP-kallet går gjennom den ekte pipelinen.
    private fun texasClient() = mockk<TexasHttpClient>().also {
        coEvery { it.exchangeToken(any(), any(), any()) } returns AccessToken("obo-token", fixedClock.instant().plusSeconds(3600))
    }

    private fun klient(endpoint: String, transport: HttpTransport? = null) = if (transport == null) {
        TiltakspengerTiltakClient(
            tiltakspengerTiltakEndpoint = endpoint,
            clock = fixedClock,
            tiltakspengerTiltakScope = "scope",
            texasClient = texasClient(),
        )
    } else {
        TiltakspengerTiltakClient(
            tiltakspengerTiltakEndpoint = endpoint,
            clock = fixedClock,
            tiltakspengerTiltakScope = "scope",
            texasClient = texasClient(),
            transport = transport,
        )
    }

    @Test
    fun `henter tiltakshistorikk med default HttpKlient-oppsett`() {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/tokenx/tiltakshistorikk")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(tiltakshistorikkJson),
                ),
            )

            runTest {
                val tiltak = klient(wiremock.baseUrl()).fetchTiltak("subject-token", Fnr.random()).getOrFail()

                tiltak.single().id shouldBe "123456"
            }
        }
    }

    @Test
    fun `sender OBO-tokenet fra TokenX som bearer`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(tiltakshistorikkJson)

        klient("http://tiltak", transport).fetchTiltak("subject-token", Fnr.random()).getOrFail()

        val kall = transport.mottatteKall.single()
        kall.metode shouldBe "GET"
        kall.uri.toString() shouldBe "http://tiltak/tokenx/tiltakshistorikk"
        kall.request.headers().firstValue("Authorization").get() shouldBe "Bearer obo-token"
        kall.request.headers().firstValue("Accept").get() shouldBe "application/json"
    }

    @Test
    fun `treff caches, så påfølgende kall for samme fnr ikke treffer nettverket`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(tiltakshistorikkJson)
        val klient = klient("http://tiltak", transport)
        val fnr = Fnr.random()

        klient.fetchTiltak("subject-token", fnr).getOrFail()
        klient.fetchTiltak("subject-token", fnr).getOrFail()

        transport.mottatteKall.size shouldBe 1
    }

    @Test
    fun `tomt svar caches ikke`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("[]")
        transport.leggIKøJson(tiltakshistorikkJson)
        val klient = klient("http://tiltak", transport)
        val fnr = Fnr.random()

        klient.fetchTiltak("subject-token", fnr).getOrFail() shouldBe emptyList()
        klient.fetchTiltak("subject-token", fnr).getOrFail().size shouldBe 1

        transport.mottatteKall.size shouldBe 2
    }

    @Test
    fun `feilstatus gir Left`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(404, body = "finnes ikke")

        val feil = klient("http://tiltak", transport).fetchTiltak("subject-token", Fnr.random()).leftOrNull()!!

        feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 404
    }

    @Test
    fun `retryer serverfeil fire ganger totalt, som den gamle ktor-klienten`() = runTest {
        val transport = FakeHttpTransport()
        repeat(4) { transport.leggIKøStatus(500, body = "nede") }

        klient("http://tiltak", transport).fetchTiltak("subject-token", Fnr.random()).leftOrNull()!!

        transport.mottatteKall.size shouldBe 4
    }

    private val tiltakshistorikkJson = """
        [
          {
            "id": "123456",
            "gjennomforing": {
              "id": "123456",
              "arenaKode": "ABIST",
              "typeNavn": "typenavn",
              "arrangornavn": "Arrangør AS",
              "deltidsprosent": 100.0,
              "visningsnavn": "Typenavn hos Arrangør AS"
            },
            "deltakelseFom": null,
            "deltakelseTom": null,
            "deltakelseStatus": "DELTAR",
            "antallDagerPerUke": null,
            "deltakelseProsent": null,
            "kilde": "KOMET"
          }
        ]
    """.trimIndent()
}
