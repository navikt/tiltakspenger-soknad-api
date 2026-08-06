package no.nav.tiltakspenger.soknad.api.pdl.client

import arrow.core.nonEmptyListOf
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.soknad.api.testutils.testTokenProvider
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PdlClientTest {
    private val fødselsnummer = "02058938710"
    private val barnFødselsnummer = "21062002856"

    // TexasHttpClient er en final klasse som ikke kan byttes ut i libs, så OBO-vekslingen mockes; selve HTTP-kallet går gjennom den ekte pipelinen.
    private fun texasClient() = mockk<TexasHttpClient>().also {
        coEvery { it.exchangeToken(any(), any(), any()) } returns AccessToken("obo-token", fixedClock.instant().plusSeconds(3600))
    }

    private fun klient(endepunkt: String, transport: HttpTransport? = null) = if (transport == null) {
        PdlClient(
            endepunkt = endepunkt,
            clock = fixedClock,
            pdlScope = "scope",
            texasClient = texasClient(),
            authTokenProvider = testTokenProvider,
        )
    } else {
        PdlClient(
            endepunkt = endepunkt,
            clock = fixedClock,
            pdlScope = "scope",
            texasClient = texasClient(),
            authTokenProvider = testTokenProvider,
            transport = transport,
        )
    }

    @Test
    fun `henter søker med default HttpKlient-oppsett`() {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                post(urlEqualTo("/graphql")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(søkerSvar),
                ),
            )

            runTest {
                val respons = klient("${wiremock.baseUrl()}/graphql").fetchSøker(fødselsnummer, "subject-token").getOrFail()

                respons.hentPerson!!.navn.single().fornavn shouldBe "Fornavn"
            }
        }
    }

    @Test
    fun `søkeroppslag med tokenx sender OBO-tokenet og PDL-headerne`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(søkerSvar)

        klient("http://pdl/graphql", transport).fetchSøker(fødselsnummer, "subject-token").getOrFail()

        val kall = transport.mottatteKall.single()
        kall.metode shouldBe "POST"
        kall.uri.toString() shouldBe "http://pdl/graphql"
        kall.request.headers().firstValue("Authorization").get() shouldBe "Bearer obo-token"
        kall.request.headers().firstValue("Tema").get() shouldBe "IND"
        kall.request.headers().firstValue("behandlingsnummer").get() shouldBe "B470"
        kall.bodyTekst shouldContain fødselsnummer
    }

    @Test
    fun `systembrukeroppslag bruker klientens systemtoken`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(søkerSvar)

        klient("http://pdl/graphql", transport).fetchSøkerSystembruker(fødselsnummer).getOrFail()

        transport.mottatteKall.single().request.headers().firstValue("Authorization").get() shouldBe "Bearer test-token"
    }

    @Test
    fun `treff caches, og cachen deles mellom tokenx- og systembrukeroppslag`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(søkerSvar)
        val klient = klient("http://pdl/graphql", transport)

        klient.fetchSøker(fødselsnummer, "subject-token").getOrFail()
        klient.fetchSøkerSystembruker(fødselsnummer).getOrFail()

        transport.mottatteKall.size shouldBe 1
    }

    @Test
    fun `svar uten hentPerson caches ikke`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"data":{"hentPerson":null,"hentGeografiskTilknytning":null}}""")
        transport.leggIKøJson(søkerSvar)
        val klient = klient("http://pdl/graphql", transport)

        klient.fetchSøkerSystembruker(fødselsnummer).getOrFail().hentPerson shouldBe null
        klient.fetchSøkerSystembruker(fødselsnummer).getOrFail().hentPerson!!.navn.single().fornavn shouldBe "Fornavn"

        transport.mottatteKall.size shouldBe 2
    }

    @Test
    fun `fetchBarn gjør et bolkoppslag med systemtoken`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(barnSvar)

        val respons = klient("http://pdl/graphql", transport).fetchBarn(nonEmptyListOf(barnFødselsnummer)).getOrFail()

        respons.hentPersonBolk.single().ident shouldBe barnFødselsnummer
        transport.mottatteKall.single().request.headers().firstValue("Authorization").get() shouldBe "Bearer test-token"
    }

    @Test
    fun `GraphQL-feil på 200 gir GraphQLFeil med feilmeldingene`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"data":null,"errors":[{"message":"Fant ikke person","extensions":{"code":"not_found","classification":"NotFound"}}]}""")

        val feil = klient("http://pdl/graphql", transport).fetchSøkerSystembruker(fødselsnummer).leftOrNull()!!

        feil.shouldBeInstanceOf<KanIkkeHentePerson.GraphQLFeil>().feilmeldinger shouldBe nonEmptyListOf("Fant ikke person")
    }

    @Test
    fun `200 uten data og uten errors gir ResponsManglerData`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("{}")

        val feil = klient("http://pdl/graphql", transport).fetchSøkerSystembruker(fødselsnummer).leftOrNull()!!

        feil.shouldBeInstanceOf<KanIkkeHentePerson.ResponsManglerData>()
    }

    @Test
    fun `feilstatus gir KallFeilet`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503, body = "pdl er nede")

        val feil = klient("http://pdl/graphql", transport).fetchSøkerSystembruker(fødselsnummer).leftOrNull()!!

        feil.shouldBeInstanceOf<KanIkkeHentePerson.KallFeilet>()
            .feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 503
    }

    @Test
    fun `godtar kun 200, ikke andre 2xx`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(202, body = søkerSvar)

        val feil = klient("http://pdl/graphql", transport).fetchSøkerSystembruker(fødselsnummer).leftOrNull()!!

        feil.shouldBeInstanceOf<KanIkkeHentePerson.KallFeilet>()
            .feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 202
    }

    @Test
    fun `navn og fødselsdato uten folkeregistermetadata kan deserialiseres`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(søkerSvarUtenFolkeregistermetadata)

        val respons = klient("http://pdl/graphql", transport).fetchSøkerSystembruker(fødselsnummer).getOrFail()

        val person = respons.toPerson(Fnr.fromString(fødselsnummer))
        person.fornavn shouldBe "Fornavn"
        person.fødselsdato shouldBe LocalDate.of(1989, 5, 2)
    }

    @Test
    fun `retryer ikke, som før migreringen`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(500, body = "nede")

        klient("http://pdl/graphql", transport).fetchSøkerSystembruker(fødselsnummer).leftOrNull()!!

        transport.mottatteKall.size shouldBe 1
    }

    private val søkerSvar = """
        {
          "data": {
            "hentPerson": {
              "navn": [
                {
                  "fornavn": "Fornavn",
                  "mellomnavn": null,
                  "etternavn": "Etternavn",
                  "metadata": { "endringer": [], "master": "FREG" },
                  "folkeregistermetadata": {
                    "aarsak": null, "ajourholdstidspunkt": null, "gyldighetstidspunkt": null,
                    "kilde": null, "opphoerstidspunkt": null, "sekvens": null
                  }
                }
              ],
              "adressebeskyttelse": [],
              "foedselsdato": [
                {
                  "foedselsdato": "1989-05-02",
                  "metadata": { "endringer": [], "master": "FREG" },
                  "folkeregistermetadata": {
                    "aarsak": null, "ajourholdstidspunkt": null, "gyldighetstidspunkt": null,
                    "kilde": null, "opphoerstidspunkt": null, "sekvens": null
                  }
                }
              ],
              "forelderBarnRelasjon": [],
              "doedsfall": []
            },
            "hentGeografiskTilknytning": {
              "gtType": "KOMMUNE", "gtKommune": "1122", "gtBydel": null, "gtLand": null
            }
          }
        }
    """.trimIndent()

    /**
     * PDL svarer `folkeregistermetadata: null` på opplysninger som ikke er mastret i Folkeregisteret.
     * Feltet er nullable i PDL-skjemaet, og var det ikke i DTO-ene våre — da feilet hele søkeroppslaget på deserialiseringen.
     */
    private val søkerSvarUtenFolkeregistermetadata = """
        {
          "data": {
            "hentPerson": {
              "navn": [
                {
                  "fornavn": "Fornavn",
                  "mellomnavn": null,
                  "etternavn": "Etternavn",
                  "metadata": { "endringer": [], "master": "PDL" },
                  "folkeregistermetadata": null
                }
              ],
              "adressebeskyttelse": [],
              "foedselsdato": [
                {
                  "foedselsdato": "1989-05-02",
                  "metadata": { "endringer": [], "master": "PDL" },
                  "folkeregistermetadata": null
                }
              ],
              "forelderBarnRelasjon": [],
              "doedsfall": []
            },
            "hentGeografiskTilknytning": null
          }
        }
    """.trimIndent()

    private val barnSvar = """
        {
          "data": {
            "hentPersonBolk": [
              {
                "ident": "$barnFødselsnummer",
                "code": "ok",
                "person": {
                  "navn": [
                    {
                      "fornavn": "Barn", "mellomnavn": null, "etternavn": "Barnesen",
                      "metadata": { "endringer": [], "master": "FREG" },
                      "folkeregistermetadata": {
                        "aarsak": null, "ajourholdstidspunkt": null, "gyldighetstidspunkt": null,
                        "kilde": null, "opphoerstidspunkt": null, "sekvens": null
                      }
                    }
                  ],
                  "adressebeskyttelse": [],
                  "foedselsdato": [
                    {
                      "foedselsdato": "2020-06-21",
                      "metadata": { "endringer": [], "master": "FREG" },
                      "folkeregistermetadata": {
                        "aarsak": null, "ajourholdstidspunkt": null, "gyldighetstidspunkt": null,
                        "kilde": null, "opphoerstidspunkt": null, "sekvens": null
                      }
                    }
                  ],
                  "doedsfall": []
                }
              }
            ]
          }
        }
    """.trimIndent()
}
