package no.nav.tiltakspenger.soknad.api.tiltak

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
import no.nav.tiltakspenger.libs.arena.tiltak.ArenaTiltaksaktivitetResponsDTO.DeltakerStatusType.FULLF
import no.nav.tiltakspenger.libs.arena.tiltak.ArenaTiltaksaktivitetResponsDTO.TiltakType.ABOPPF
import no.nav.tiltakspenger.soknad.api.TILTAK_PATH
import no.nav.tiltakspenger.soknad.api.configureTestApplication
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.UGRADERT
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class TiltakRoutesTest {
    private val mockOAuth2Server = MockOAuth2Server()

    @BeforeAll
    fun setup() = mockOAuth2Server.start(8080)

    @AfterAll
    fun after() = mockOAuth2Server.shutdown()

    private val mockedPdlService = mockk<PdlService>().also { mock ->
        coEvery { mock.hentAdressebeskyttelse(any(), any(), any()) } returns UGRADERT
    }

    val mockedTiltak = TiltakDto(
        listOf(
            TiltaksdeltakelseDto(
                aktivitetId = "123456",
                type = ABOPPF,
                typeNavn = "typenavn",
                arenaRegistrertPeriode = Deltakelsesperiode(null, null),
                arrangør = "Testarrangør AS",
                status = FULLF,
            ),
        ),
    )

    private val mockedTiltakservice = mockk<TiltakService>().also { mock ->
        coEvery { mock.hentTiltak(any(), any()) } returns mockedTiltak
    }

    val testFødselsnummer = "123"

    private fun issueTestToken(
        issuer: String = "tokendings",
        clientId: String = "testClientId",
        claims: Map<String, String> = mapOf(
            "acr" to "Level4",
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
    fun `get på tiltak-endepunkt skal svare med tiltak fra tiltakservice hvis tokenet er gyldig og validerer ok`() {
        val token = issueTestToken()

        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    jackson()
                }
            }

            configureTestApplication(
                pdlService = mockedPdlService,
                tiltakService = mockedTiltakservice,
            )
            runBlocking {
                val response = client.get(TILTAK_PATH) {
                    contentType(type = ContentType.Application.Json)
                    header("Authorization", "Bearer ${token.serialize()}")
                }
                Assertions.assertEquals(HttpStatusCode.OK, response.status)
                val body: TiltakDto = response.body()
                assertEquals(mockedTiltak.tiltak, body.tiltak)
            }
        }
    }

    @Test
    fun `get på tiltak-endepunkt skal kalle på TiltakService med fødselsnummeret som ligger bakt inn i pid claim i tokenet`() {
        val token = issueTestToken()

        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    jackson()
                }
            }

            configureTestApplication(
                pdlService = mockedPdlService,
                tiltakService = mockedTiltakservice,
            )
            runBlocking {
                client.get(TILTAK_PATH) {
                    contentType(type = ContentType.Application.Json)
                    header("Authorization", "Bearer ${token.serialize()}")
                }
                coVerify { mockedTiltakservice.hentTiltak(token.serialize(), any()) }
            }
        }
    }

    @Test
    fun `get på tiltak-endepunkt skal returnere 401 dersom token mangler`() {
        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    jackson()
                }
            }

            configureTestApplication(
                pdlService = mockedPdlService,
                tiltakService = mockedTiltakservice,
            )
            runBlocking {
                val response = client.get(TILTAK_PATH) {
                    contentType(type = ContentType.Application.Json)
                }
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }
    }

    @Test
    fun `get på tiltak-endepunkt skal returnere 401 dersom token kommer fra ugyldig issuer`() {
        val tokenMedUgyldigIssuer = issueTestToken(issuer = "ugyldigIssuer")

        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    jackson()
                }
            }

            configureTestApplication(
                pdlService = mockedPdlService,
                tiltakService = mockedTiltakservice,
            )
            runBlocking {
                val response = client.get(TILTAK_PATH) {
                    contentType(type = ContentType.Application.Json)
                    header("Authorization", "Bearer ${tokenMedUgyldigIssuer.serialize()}")
                }
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }
    }

    @Test
    fun `get på tiltak-endepunkt skal returnere 401 dersom token mangler acr=Level4 claim`() {
        val tokenMedManglendeClaim = issueTestToken(claims = mapOf("pid" to testFødselsnummer))

        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    jackson()
                }
            }

            configureTestApplication(
                pdlService = mockedPdlService,
                tiltakService = mockedTiltakservice,
            )
            runBlocking {
                val response = client.get(TILTAK_PATH) {
                    contentType(type = ContentType.Application.Json)
                    header("Authorization", "Bearer ${tokenMedManglendeClaim.serialize()}")
                }
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }
    }
}
