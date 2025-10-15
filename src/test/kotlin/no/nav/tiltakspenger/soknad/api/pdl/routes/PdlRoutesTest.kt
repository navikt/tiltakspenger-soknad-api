package no.nav.tiltakspenger.soknad.api.pdl.routes

import com.nimbusds.jwt.SignedJWT
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.libs.texas.client.TexasIntrospectionResponse
import no.nav.tiltakspenger.soknad.api.configureTestApplication
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.pdl.Person
import no.nav.tiltakspenger.soknad.api.pdl.routes.dto.PersonDTO
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakService
import no.nav.tiltakspenger.soknad.api.util.getGyldigTexasIntrospectionResponse
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

internal class PdlRoutesTest {
    private val texasClient = mockk<TexasHttpClient>()
    private val pdlService = mockk<PdlService>()
    private val tiltakservice = mockk<TiltakService>(relaxed = true)
    private val mockOAuth2Server = MockOAuth2Server()

    private val testFødselsnummer = "12345678910"

    private val mockedPerson = Person(
        fornavn = "foo",
        etternavn = "bar",
        mellomnavn = "baz",
        fødselsdato = LocalDate.MAX,
        adressebeskyttelseGradering = AdressebeskyttelseGradering.UGRADERT,
        erDød = false,
    )

    @BeforeEach
    fun setupMocks() {
        clearMocks(texasClient, pdlService)
        coEvery { pdlService.hentPersonaliaMedBarn(any(), any(), any()) } returns mockedPerson.toPersonDTO()
    }

    @BeforeAll
    fun setup() = mockOAuth2Server.start(8080)

    @AfterAll
    fun after() = mockOAuth2Server.shutdown()

    @Test
    fun `get på personalia-endepunkt skal svare med personalia fra PDLService hvis tokenet er gyldig og validerer ok`() {
        val token = issueTestToken()
        coEvery { texasClient.introspectToken(any(), any()) } returns getGyldigTexasIntrospectionResponse(
            fnr = token.jwtClaimsSet.claims["pid"].toString(),
            acr = token.jwtClaimsSet.claims["acr"].toString(),
        )

        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    jackson()
                }
            }
            configureTestApplication(
                texasClient = texasClient,
                pdlService = pdlService,
                tiltakService = tiltakservice,
            )
            runBlocking {
                val response = client.get("/personalia") {
                    contentType(type = ContentType.Application.Json)
                    header("Authorization", "Bearer ${token.serialize()}")
                }
                Assertions.assertEquals(HttpStatusCode.Companion.OK, response.status)
                val body: PersonDTO = response.body()
                assertEquals(mockedPerson.fornavn, body.fornavn)
                assertEquals(mockedPerson.etternavn, body.etternavn)
                assertEquals(mockedPerson.mellomnavn, body.mellomnavn)
            }
        }
    }

    @Test
    fun `get på personalia-endepunkt skal kalle på PDLService med fødselsnummeret som ligger bakt inn i pid claim i tokenet`() {
        val token = issueTestToken()
        coEvery { texasClient.introspectToken(any(), any()) } returns getGyldigTexasIntrospectionResponse(
            fnr = token.jwtClaimsSet.claims["pid"].toString(),
            acr = token.jwtClaimsSet.claims["acr"].toString(),
        )

        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    jackson()
                }
            }

            configureTestApplication(
                texasClient = texasClient,
                pdlService = pdlService,
                tiltakService = tiltakservice,
            )
            runBlocking {
                client.get("/personalia") {
                    contentType(type = ContentType.Application.Json)
                    header("Authorization", "Bearer ${token.serialize()}")
                }
                coVerify { pdlService.hentPersonaliaMedBarn(testFødselsnummer, any(), any(), any()) }
            }
        }
    }

    @Test
    fun `get på personalia-endepunkt skal returnere 401 dersom token mangler`() {
        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    jackson()
                }
            }

            configureTestApplication(
                texasClient = texasClient,
                pdlService = pdlService,
                tiltakService = tiltakservice,
            )
            runBlocking {
                val response = client.get("/personalia") {
                    contentType(type = ContentType.Application.Json)
                }
                assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
            }
        }
    }

    @Test
    fun `get på personalia-endepunkt skal returnere 401 dersom token kommer fra ugyldig issuer`() {
        val tokenMedUgyldigIssuer = issueTestToken(issuer = "ugyldigIssuer")
        coEvery { texasClient.introspectToken(any(), any()) } returns TexasIntrospectionResponse(
            active = false,
            error = "Ugyldig issuer",
            groups = null,
            roles = null,
        )

        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    jackson()
                }
            }

            configureTestApplication(
                texasClient = texasClient,
                pdlService = pdlService,
                tiltakService = tiltakservice,
            )
            runBlocking {
                val response = client.get("/personalia") {
                    contentType(type = ContentType.Application.Json)
                    header("Authorization", "Bearer ${tokenMedUgyldigIssuer.serialize()}")
                }
                assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
            }
        }
    }

    @Test
    fun `get på personalia-endepunkt skal returnere 401 dersom token mangler acr=Level4 claim`() {
        val tokenMedManglendeClaim = issueTestToken(claims = mapOf("pid" to testFødselsnummer))
        coEvery { texasClient.introspectToken(any(), any()) } returns getGyldigTexasIntrospectionResponse(
            fnr = tokenMedManglendeClaim.jwtClaimsSet.claims["pid"].toString(),
            acr = "",
        )

        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    jackson()
                }
            }

            configureTestApplication(
                texasClient = texasClient,
                pdlService = pdlService,
                tiltakService = tiltakservice,
            )
            runBlocking {
                val response = client.get("/personalia") {
                    contentType(type = ContentType.Application.Json)
                    header("Authorization", "Bearer ${tokenMedManglendeClaim.serialize()}")
                }
                assertEquals(HttpStatusCode.Companion.Unauthorized, response.status)
            }
        }
    }

    private fun issueTestToken(
        issuer: String = "tokendings",
        clientId: String = "testClientId",
        claims: Map<String, String> = mapOf(
            "acr" to "idporten-loa-high",
            "pid" to testFødselsnummer,
        ),
    ): SignedJWT {
        return mockOAuth2Server.issueToken(
            issuer,
            clientId,
            DefaultOAuth2TokenCallback(
                audience = listOf("audience"),
                claims = claims,
            ),
        )
    }
}
