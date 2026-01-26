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
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.libs.texas.client.TexasIntrospectionResponse
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO
import no.nav.tiltakspenger.libs.tiltak.TiltakshistorikkDTO
import no.nav.tiltakspenger.soknad.api.TILTAK_PATH
import no.nav.tiltakspenger.soknad.api.configureTestApplication
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.FORTROLIG
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.STRENGT_FORTROLIG
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.UGRADERT
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.util.getGyldigTexasIntrospectionResponse
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

internal class TiltakRoutesTest {
    private val texasClient = mockk<TexasHttpClient>()
    private val pdlService = mockk<PdlService>()
    private val tiltakspengerTiltakClient = mockk<TiltakspengerTiltakClient>()
    private val tiltakservice = TiltakService(tiltakspengerTiltakClient)
    private val mockOAuth2Server = MockOAuth2Server()
    private val gjennomforingId = UUID.randomUUID().toString()

    private val mockedTiltak =
        listOf(
            TiltaksdeltakelseDto(
                aktivitetId = "123456",
                type = TiltakResponsDTO.TiltakType.ABOPPF,
                typeNavn = "typenavn",
                arenaRegistrertPeriode = Deltakelsesperiode(null, null),
                arrangør = "Testarrangør AS",
                gjennomforingId = gjennomforingId,
                visningsnavn = "Typenavn hos Testarrangør AS",
            ),
        )
    private val testFødselsnummer = "12345678910"

    @BeforeEach
    fun setupMocks() {
        clearMocks(texasClient, pdlService, tiltakspengerTiltakClient)
        coEvery { pdlService.hentAdressebeskyttelse(any(), any(), any()) } returns UGRADERT
        coEvery { tiltakspengerTiltakClient.fetchTiltak(any(), any()) } returns Result.success(mockTiltakspengerTiltakResponse(arrangør = "Testarrangør AS"))
    }

    @BeforeAll
    fun setup() = mockOAuth2Server.start(8080)

    @AfterAll
    fun after() = mockOAuth2Server.shutdown()

    @Test
    fun `get på tiltak-endepunkt skal svare med tiltak fra tiltakservice hvis tokenet er gyldig og validerer ok`() {
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
                val response = client.get(TILTAK_PATH) {
                    contentType(type = ContentType.Application.Json)
                    header("Authorization", "Bearer ${token.serialize()}")
                }
                Assertions.assertEquals(HttpStatusCode.OK, response.status)
                val body: TiltakDto = response.body()
                assertEquals(mockedTiltak, body.tiltak)
            }
        }
    }

    @Test
    fun `get på tiltak-endepunkt skal svare med tiltak fra tiltakservice hvis tokenet er gyldig, også for token med gammelt acr-claim`() {
        val tokenAcrLevel4 = issueTestTokenOldAcr()
        coEvery { texasClient.introspectToken(any(), any()) } returns getGyldigTexasIntrospectionResponse(
            fnr = tokenAcrLevel4.jwtClaimsSet.claims["pid"].toString(),
            acr = tokenAcrLevel4.jwtClaimsSet.claims["acr"].toString(),
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
                val response = client.get(TILTAK_PATH) {
                    contentType(type = ContentType.Application.Json)
                    header("Authorization", "Bearer ${tokenAcrLevel4.serialize()}")
                }
                Assertions.assertEquals(HttpStatusCode.OK, response.status)
                val body: TiltakDto = response.body()
                assertEquals(mockedTiltak, body.tiltak)
            }
        }
    }

    @Test
    fun `get på tiltak-endepunkt skal fjerne arrangørnavn for en søker med adressebeskyttelse`() {
        val token = issueTestToken()
        coEvery { texasClient.introspectToken(any(), any()) } returns getGyldigTexasIntrospectionResponse(
            fnr = token.jwtClaimsSet.claims["pid"].toString(),
            acr = token.jwtClaimsSet.claims["acr"].toString(),
        )
        coEvery { pdlService.hentAdressebeskyttelse(any(), any(), any()) } returns FORTROLIG andThen STRENGT_FORTROLIG andThen STRENGT_FORTROLIG_UTLAND

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
                listOf(FORTROLIG, STRENGT_FORTROLIG, STRENGT_FORTROLIG_UTLAND).forEach { _ ->
                    val response = client.get(TILTAK_PATH) {
                        contentType(type = ContentType.Application.Json)
                        header("Authorization", "Bearer ${token.serialize()}")
                    }
                    Assertions.assertEquals(HttpStatusCode.OK, response.status)
                    val body: TiltakDto = response.body()

                    assertEquals("", body.tiltak.first().arrangør)
                }
            }
        }
    }

    @Test
    fun `get på tiltak-endepunkt skal kalle på TiltakService med fødselsnummeret som ligger bakt inn i pid claim i tokenet`() {
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
                client.get(TILTAK_PATH) {
                    contentType(type = ContentType.Application.Json)
                    header("Authorization", "Bearer ${token.serialize()}")
                }
                coVerify { tiltakspengerTiltakClient.fetchTiltak(token.serialize(), Fnr.fromString(testFødselsnummer)) }
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
                texasClient = texasClient,
                pdlService = pdlService,
                tiltakService = tiltakservice,
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
                val response = client.get(TILTAK_PATH) {
                    contentType(type = ContentType.Application.Json)
                    header("Authorization", "Bearer ${tokenMedManglendeClaim.serialize()}")
                }
                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }
    }

    private fun issueTestTokenOldAcr(
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

    private fun mockTiltakspengerTiltakResponse(arrangør: String = "Arrangør AS") =
        listOf(
            TiltakshistorikkDTO(
                id = "123456",
                gjennomforing = TiltakshistorikkDTO.GjennomforingDTO(
                    id = gjennomforingId,
                    arenaKode = TiltakResponsDTO.TiltakType.ABOPPF,
                    typeNavn = "typenavn",
                    arrangornavn = arrangør,
                    deltidsprosent = 100.0,
                    visningsnavn = "Typenavn hos $arrangør",
                ),
                deltakelseFom = null,
                deltakelseTom = null,
                deltakelseStatus = TiltakResponsDTO.DeltakerStatusDTO.DELTAR,
                antallDagerPerUke = null,
                kilde = TiltakshistorikkDTO.Kilde.KOMET,
                deltakelseProsent = null,
            ),
        )
}
