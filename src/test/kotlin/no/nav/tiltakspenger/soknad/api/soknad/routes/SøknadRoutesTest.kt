package no.nav.tiltakspenger.soknad.api.soknad.routes

import com.nimbusds.jwt.SignedJWT
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.libs.texas.client.TexasIntrospectionResponse
import no.nav.tiltakspenger.soknad.api.antivirus.AvService
import no.nav.tiltakspenger.soknad.api.configureTestApplication
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.UGRADERT
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.pdl.Person
import no.nav.tiltakspenger.soknad.api.pdl.routes.dto.PersonDTO
import no.nav.tiltakspenger.soknad.api.soknad.FylkeService
import no.nav.tiltakspenger.soknad.api.soknad.NySøknadService
import no.nav.tiltakspenger.soknad.api.soknad.SøknadRepo
import no.nav.tiltakspenger.soknad.api.util.getGyldigTexasIntrospectionResponse
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class SøknadRoutesTest {
    private val texasClient = mockk<TexasHttpClient>()
    private val pdlService = mockk<PdlService>()
    private val avService = mockk<AvService>(relaxed = true)

    companion object {
        private val mockOAuth2Server = MockOAuth2Server()

        @JvmStatic
        @BeforeAll
        fun setup(): Unit = mockOAuth2Server.start(8080)

        @JvmStatic
        @AfterAll
        fun after(): Unit = mockOAuth2Server.shutdown()
    }

    @BeforeEach
    fun setupMocks() {
        clearMocks(texasClient, pdlService)
        coEvery { pdlService.hentPerson(any(), any(), any()) } returns Person(
            fornavn = "fornavn",
            mellomnavn = null,
            etternavn = "etternavn",
            adressebeskyttelseGradering = UGRADERT,
            fødselsdato = LocalDate.now().minusYears(35),
            forelderBarnRelasjon = emptyList(),
            erDød = false,
            geografiskTilknytning = "1122",
        )
        coEvery { pdlService.hentPersonaliaMedBarn(any(), any(), any()) } returns PersonDTO(
            fornavn = "fornavn",
            mellomnavn = null,
            etternavn = "etternavn",
            barn = emptyList(),
            harFylt18År = true,
        )
    }

    @Test
    fun `post med ugyldig token skal gi 401`() {
        coEvery { texasClient.introspectToken(any(), any()) } returns TexasIntrospectionResponse(
            active = false,
            error = "Ugyldig token",
            groups = null,
            roles = null,
        )
        testApplication {
            configureTestApplication(
                texasClient = texasClient,
            )
            val response = client.post("/soknad") {
                header("Authorization", "Bearer ugyldigtoken")
                setBody(
                    MultiPartFormDataContent(
                        formData {},
                        "WebAppBoundary",
                        ContentType.MultiPart.FormData.withParameter("boundary", "WebAppBoundary"),
                    ),
                )
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `post med token som har ugyldig acr claim skal gi 401`() {
        val token = issueTestToken(acr = "Level3")
        coEvery { texasClient.introspectToken(any(), any()) } returns getGyldigTexasIntrospectionResponse(
            fnr = token.jwtClaimsSet.claims["pid"].toString(),
            acr = token.jwtClaimsSet.claims["acr"].toString(),
        )

        testApplication {
            configureTestApplication(
                texasClient = texasClient,
            )
            val response = client.post("/soknad") {
                header("Authorization", "Bearer ${token.serialize()}")
                setBody(
                    MultiPartFormDataContent(
                        formData {},
                        "WebAppBoundary",
                        ContentType.MultiPart.FormData.withParameter("boundary", "WebAppBoundary"),
                    ),
                )
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `post med token som har expiret utenfor leeway skal gi 401`() {
        val token = issueTestToken(expiry = -60L)
        coEvery { texasClient.introspectToken(any(), any()) } returns TexasIntrospectionResponse(
            active = false,
            error = "Utløpt",
            groups = null,
            roles = null,
        )

        testApplication {
            configureTestApplication(
                texasClient = texasClient,
            )
            val response = client.post("/soknad") {
                header("Authorization", "Bearer ${token.serialize()}")
                setBody(
                    MultiPartFormDataContent(
                        formData {},
                        "WebAppBoundary",
                        ContentType.MultiPart.FormData.withParameter("boundary", "WebAppBoundary"),
                    ),
                )
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `post på soknad-endepunkt skal svare med 400 hvis taInnSøknadSomMultipart svarer med BadRequest`() {
        mockkStatic("no.nav.tiltakspenger.soknad.api.soknad.routes.SoknadRequestMapperKt")
        coEvery { taInnSøknadSomMultipart(any()) } throws BadRequestException("1")
        val token = issueTestToken()
        coEvery { texasClient.introspectToken(any(), any()) } returns getGyldigTexasIntrospectionResponse(
            fnr = token.jwtClaimsSet.claims["pid"].toString(),
            acr = token.jwtClaimsSet.claims["acr"].toString(),
        )

        testApplication {
            configureTestApplication(
                texasClient = texasClient,
            )
            val response = client.post("/soknad") {
                header("Authorization", "Bearer ${token.serialize()}")
                setBody(
                    MultiPartFormDataContent(
                        formData {},
                        "WebAppBoundary",
                        ContentType.MultiPart.FormData.withParameter("boundary", "WebAppBoundary"),
                    ),
                )
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `post på soknad-endepunkt skal svare med 400 hvis søknadJson ikke er gyldig`() {
        mockkStatic("no.nav.tiltakspenger.soknad.api.soknad.routes.SoknadRequestMapperKt")
        coEvery { taInnSøknadSomMultipart(any()) } throws RequestValidationException(
            "søknadJson",
            listOf("Kvalifisering fra dato må være tidligere eller lik til dato"),
        )
        val token = issueTestToken()
        coEvery { texasClient.introspectToken(any(), any()) } returns getGyldigTexasIntrospectionResponse(
            fnr = token.jwtClaimsSet.claims["pid"].toString(),
            acr = token.jwtClaimsSet.claims["acr"].toString(),
        )

        testApplication {
            configureTestApplication(
                texasClient = texasClient,
            )
            val response = client.post("/soknad") {
                header("Authorization", "Bearer ${token.serialize()}")
                setBody(
                    MultiPartFormDataContent(
                        formData {},
                        "WebAppBoundary",
                        ContentType.MultiPart.FormData.withParameter("boundary", "WebAppBoundary"),
                    ),
                )
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `post på soknad-endepunkt skal svare med 201 Created ved gyldig søknad `() {
        mockkStatic("no.nav.tiltakspenger.soknad.api.soknad.routes.SoknadRequestMapperKt")
        coEvery { taInnSøknadSomMultipart(any()) } returns Pair(mockSpørsmålsbesvarelser(), emptyList())
        val søknadRepoMock = mockk<SøknadRepo>().also { mock ->
            coEvery { mock.hentBrukersSøknader(any(), any()) } returns emptyList()
            coEvery { mock.lagre(any()) } returns Unit
        }
        val fylkeService = mockk<FylkeService>().also { mock ->
            every { mock.brukersFylkeRutesTilTpsak(any(), any()) } returns false
        }
        val nySøknadService = NySøknadService(søknadRepoMock, fylkeService)

        val token = issueTestToken()
        coEvery { texasClient.introspectToken(any(), any()) } returns getGyldigTexasIntrospectionResponse(
            fnr = token.jwtClaimsSet.claims["pid"].toString(),
            acr = token.jwtClaimsSet.claims["acr"].toString(),
        )

        testApplication {
            configureTestApplication(
                texasClient = texasClient,
                avService = avService,
                pdlService = pdlService,
                nySøknadService = nySøknadService,
            )
            val response = client.post("/soknad") {
                header("Authorization", "Bearer ${token.serialize()}")
                setBody(
                    MultiPartFormDataContent(
                        formData {},
                        "WebAppBoundary",
                        ContentType.MultiPart.FormData.withParameter("boundary", "WebAppBoundary"),
                    ),
                )
            }
            assertEquals(HttpStatusCode.Created, response.status)
        }
    }

    private fun issueTestToken(acr: String = "idporten-loa-high", expiry: Long = 3600): SignedJWT {
        return mockOAuth2Server.issueToken(
            issuerId = "tokendings",
            audience = "audience",
            claims = mapOf(
                "acr" to acr,
                "pid" to "12345678910",
            ),
            expiry = expiry,
        )
    }
}
