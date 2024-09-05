package no.nav.tiltakspenger.soknad.api.tiltak

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.TiltakDTO
import no.nav.tiltakspenger.soknad.api.TILTAK_PATH
import no.nav.tiltakspenger.soknad.api.configureTestApplication
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.FORTROLIG
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.STRENGT_FORTROLIG
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.UGRADERT
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
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

    val mockedTiltak =
        listOf(
            TiltaksdeltakelseDto(
                aktivitetId = "123456",
                type = TiltakResponsDTO.TiltakType.ABOPPF,
                typeNavn = "typenavn",
                arenaRegistrertPeriode = Deltakelsesperiode(null, null),
                arrangør = "Testarrangør AS",
                // status = FULLF,
            ),
        )

    private val mockedTiltakservice = mockk<TiltakService>().also { mock ->
        coEvery { mock.hentTiltak(any(), any()) } returns mockedTiltak
    }

    val testFødselsnummer = "123"

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
                assertEquals(mockedTiltak, body.tiltak)
            }
        }
    }

    @Test
    fun `get på tiltak-endepunkt skal svare med tiltak fra tiltakservice hvis tokenet er gyldig, også for token med gammelt acr-claim`() {
        val tokenAcrLevel4 = issueTestTokenOldAcr()

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
                    header("Authorization", "Bearer ${tokenAcrLevel4.serialize()}")
                }
                Assertions.assertEquals(HttpStatusCode.OK, response.status)
                val body: TiltakDto = response.body()
                assertEquals(mockedTiltak, body.tiltak)
            }
        }
    }

    @Test
    fun `get på tiltak-endepunk skal fjerne arrangørnavn for en søker med adressebeskyttelse`() {
        val token = issueTestToken()

        val mockedPdlServiceKode6og7 = mockk<PdlService>().also { mock ->
            coEvery {
                mock.hentAdressebeskyttelse(
                    any(),
                    any(),
                    any(),
                )
            } returns FORTROLIG andThen STRENGT_FORTROLIG andThen STRENGT_FORTROLIG_UTLAND
        }

        val tiltakspengerTiltakClient = mockk<TiltakspengerTiltakClient>().also { mock ->
            coEvery { mock.fetchTiltak(any()) } returns Result.success(
                mockTiltakspengerTiltakResponse("Testarrangør AS"),
            )
        }
        val tiltakService = TiltakService(
            applicationConfig = ApplicationConfig("application.test.conf"),
            tiltakspengerTiltakClient = tiltakspengerTiltakClient,
        )

        testApplication {
            val client = createClient {
                install(ContentNegotiation) {
                    jackson {
                        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                        registerModule(JavaTimeModule())
                        registerModule(KotlinModule.Builder().build())
                    }
                }
            }

            configureTestApplication(
                pdlService = mockedPdlServiceKode6og7,
                tiltakService = tiltakService,
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

/*    fun mockArenaTiltaksaktivitetResponsDTO(arrangør: String = "Arrangør AS") =
        ArenaTiltaksaktivitetResponsDTO(
            tiltaksaktiviteter = listOf(
                ArenaTiltaksaktivitetResponsDTO.TiltaksaktivitetDTO(
                    tiltakType = ArenaTiltaksaktivitetResponsDTO.TiltakType.ABIST,
                    aktivitetId = "",
                    tiltakLokaltNavn = "",
                    arrangoer = arrangør,
                    bedriftsnummer = "123456789",
                    deltakelsePeriode = null,
                    deltakelseProsent = 100f,
                    deltakerStatusType = ArenaTiltaksaktivitetResponsDTO.DeltakerStatusType.DELAVB,
                    statusSistEndret = null,
                    begrunnelseInnsoeking = null,
                    antallDagerPerUke = null,
                ),
            ),
        )*/

    private fun mockTiltakspengerTiltakResponse(arrangør: String = "Arrangør AS") =
        listOf(
            TiltakDTO(
                id = "123456",
                gjennomforing = TiltakResponsDTO.GjennomføringDTO(
                    id = "123456",
                    arenaKode = TiltakResponsDTO.TiltakType.ABOPPF,
                    typeNavn = "typenavn",
                    arrangørnavn = arrangør,
                ),
                deltakelseFom = LocalDate.now().minusDays(10),
                deltakelseTom = LocalDate.now().plusDays(10),
                deltakelseStatus = TiltakResponsDTO.DeltakerStatusDTO.DELTAR,
                deltakelseDagerUke = null,
                kilde = "Komet",
                deltakelseProsent = null,
                registrertDato = LocalDateTime.now(),
            ),
        )
}
