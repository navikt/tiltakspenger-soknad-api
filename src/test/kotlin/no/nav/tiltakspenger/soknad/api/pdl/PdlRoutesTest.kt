package no.nav.tiltakspenger.soknad.api.pdl

import com.nimbusds.jwt.SignedJWT
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.tiltakspenger.soknad.api.configureTestApplication
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

internal class PdlRoutesTest {
    private val mockOAuth2Server = MockOAuth2Server()

    @BeforeAll
    fun setup() = mockOAuth2Server.start(8080)

    @AfterAll
    fun after() = mockOAuth2Server.shutdown()

    private val mockedPerson = Person(
        fornavn = "foo",
        etternavn = "bar",
        mellomnavn = "baz",
        fødselsdato = LocalDate.MAX,
        adressebeskyttelseGradering = AdressebeskyttelseGradering.UGRADERT,
        erDød = false,
    )

    private val mockedPdlService = mockk<PdlService>().also { mock ->
        coEvery { mock.hentPersonaliaMedBarn(any(), any(), any()) } returns mockedPerson.toPersonDTO()
    }

    val testFødselsnummer = "123"

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

    @Test
    fun `get på personalia-endepunkt skal svare med personalia fra PDLService hvis tokenet er gyldig og validerer ok`() {
        val token = issueTestToken()

        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    jackson()
                }
            }

            configureTestApplication(pdlService = mockedPdlService)
            runBlocking {
                val response = client.get("/personalia") {
                    contentType(type = ContentType.Application.Json)
                    header("Authorization", "Bearer ${token.serialize()}")
                }
                Assertions.assertEquals(HttpStatusCode.OK, response.status)
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

        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    jackson()
                }
            }

            configureTestApplication(pdlService = mockedPdlService)
            runBlocking {
                client.get("/personalia") {
                    contentType(type = ContentType.Application.Json)
                    header("Authorization", "Bearer ${token.serialize()}")
                }
                coVerify { mockedPdlService.hentPersonaliaMedBarn(testFødselsnummer, any(), any()) }
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

            configureTestApplication(pdlService = mockedPdlService)
            runBlocking {
                val response = client.get("/personalia") {
                    contentType(type = ContentType.Application.Json)
                }
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }
    }

    @Test
    fun `get på personalia-endepunkt skal returnere 401 dersom token kommer fra ugyldig issuer`() {
        val tokenMedUgyldigIssuer = issueTestToken(issuer = "ugyldigIssuer")

        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    jackson()
                }
            }

            configureTestApplication(pdlService = mockedPdlService)
            runBlocking {
                val response = client.get("/personalia") {
                    contentType(type = ContentType.Application.Json)
                    header("Authorization", "Bearer ${tokenMedUgyldigIssuer.serialize()}")
                }
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }
    }

    @Test
    fun `get på personalia-endepunkt skal returnere 401 dersom token mangler acr=idporten-loa-high claim`() {
        val tokenMedManglendeClaim = issueTestToken(claims = mapOf("pid" to testFødselsnummer))

        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    jackson()
                }
            }

            configureTestApplication(pdlService = mockedPdlService)
            runBlocking {
                val response = client.get("/personalia") {
                    contentType(type = ContentType.Application.Json)
                    header("Authorization", "Bearer ${tokenMedManglendeClaim.serialize()}")
                }
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }
    }
}
