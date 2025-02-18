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
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.tiltakspenger.soknad.api.antivirus.AvService
import no.nav.tiltakspenger.soknad.api.configureTestApplication
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.pdl.PersonDTO
import no.nav.tiltakspenger.soknad.api.soknad.NySøknadService
import no.nav.tiltakspenger.soknad.api.soknad.SøknadRepo
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

internal class SøknadRoutesTest {

    private val pdlServiceMock = mockk<PdlService>().also { mock ->
        coEvery { mock.hentPersonaliaMedBarn(any(), any(), any()) } returns PersonDTO(
            fornavn = "fornavn",
            mellomnavn = null,
            etternavn = "etternavn",
            barn = emptyList(),
            harFylt18År = true,
        )
    }
    private val avServiceMock = mockk<AvService>().also { mock ->
        coEvery { mock.gjørVirussjekkAvVedlegg(any()) } returns Unit
    }

    companion object {
        private val mockOAuth2Server = MockOAuth2Server()

        @JvmStatic
        @BeforeAll
        fun setup(): Unit = mockOAuth2Server.start(8080)

        @JvmStatic
        @AfterAll
        fun after(): Unit = mockOAuth2Server.shutdown()
    }

    private fun issueTestToken(acr: String = "idporten-loa-high", expiry: Long = 3600): SignedJWT {
        return mockOAuth2Server.issueToken(
            issuerId = "tokendings",
            audience = "audience",
            claims = mapOf(
                "acr" to acr,
                "pid" to "123",
            ),
            expiry = expiry,
        )
    }

    @Test
    fun `post med ugyldig token skal gi 401`() {
        testApplication {
            configureTestApplication()
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

        testApplication {
            configureTestApplication()
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

        testApplication {
            configureTestApplication()
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

        testApplication {
            configureTestApplication()
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

        testApplication {
            configureTestApplication()
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
        coEvery { taInnSøknadSomMultipart(any()) } returns Pair(mockk(), emptyList())
        val søknadRepoMock = mockk<SøknadRepo>().also { mock ->
            coEvery { mock.hentBrukersSøknader(any(), any()) } returns emptyList()
            coEvery { mock.lagre(any()) } returns Unit
        }
        val nySøknadService = NySøknadService(søknadRepoMock)

        val token = issueTestToken()

        testApplication {
            configureTestApplication(
                avService = avServiceMock,
                pdlService = pdlServiceMock,
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
}
