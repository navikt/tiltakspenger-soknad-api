package no.nav.tiltakspenger.soknad.api.soknad.routes

import arrow.core.left
import com.nimbusds.jwt.JWT
import io.kotest.matchers.shouldBe
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
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.libs.texas.client.TexasIntrospectionResponse
import no.nav.tiltakspenger.soknad.api.antivirus.AvService
import no.nav.tiltakspenger.soknad.api.configureTestApplication
import no.nav.tiltakspenger.soknad.api.mockSpørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.pdl.routes.dto.PersonDTO
import no.nav.tiltakspenger.soknad.api.soknad.KunneIkkeMottaNySøknad
import no.nav.tiltakspenger.soknad.api.soknad.NySøknadService
import no.nav.tiltakspenger.soknad.api.soknad.SøknadRepo
import no.nav.tiltakspenger.soknad.api.util.getGyldigTexasIntrospectionResponse
import no.nav.tiltakspenger.soknad.api.util.lagTestToken
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SøknadRoutesTest {
    private val texasClient = mockk<TexasHttpClient>()
    private val pdlService = mockk<PdlService>()
    private val avService = mockk<AvService>(relaxed = true)

    @BeforeEach
    fun setupMocks() {
        clearMocks(texasClient, pdlService)
        coEvery { pdlService.hentPersonaliaMedBarn(any(), any(), any()) } returns PersonDTO(
            fornavn = "fornavn",
            mellomnavn = null,
            etternavn = "etternavn",
            barn = emptyList(),
            harFylt18År = true,
        )
    }

    @AfterEach
    fun tearDown() {
        // mockkStatic muterer global statisk tilstand; unmock så det ikke lekker til andre testklasser.
        unmockkAll()
    }

    // TODO jah: taInnSøknadSomMultipart mockes bort med mockkStatic i flere tester her.
    // Dette kunne vært ekte e2e ved å sende reell multipart-body inn på ruten (som `post med token ... 201`-testen), så både multipart-parsing og validering dekkes uten statisk mock.

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
            response.status shouldBe HttpStatusCode.Unauthorized
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
            response.status shouldBe HttpStatusCode.Unauthorized
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
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post på soknad-endepunkt skal svare med 400 hvis taInnSøknadSomMultipart svarer med BadRequest`() {
        mockkStatic("no.nav.tiltakspenger.soknad.api.soknad.routes.SoknadRequestMapperKt")
        coEvery { taInnSøknadSomMultipart(any(), any()) } throws BadRequestException("1")
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
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `post på soknad-endepunkt skal svare med 400 hvis søknadJson ikke er gyldig`() {
        mockkStatic("no.nav.tiltakspenger.soknad.api.soknad.routes.SoknadRequestMapperKt")
        coEvery { taInnSøknadSomMultipart(any(), any()) } throws RequestValidationException(
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
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `post på soknad-endepunkt skal svare med 201 Created ved gyldig søknad `() {
        mockkStatic("no.nav.tiltakspenger.soknad.api.soknad.routes.SoknadRequestMapperKt")
        coEvery { taInnSøknadSomMultipart(any(), any()) } returns Pair(mockSpørsmålsbesvarelser(), emptyList())
        val søknadRepoMock = mockk<SøknadRepo>().also { mock ->
            coEvery { mock.hentBrukersSøknader(any(), any()) } returns emptyList()
            coEvery { mock.lagre(any()) } returns Unit
        }
        val nySøknadService = NySøknadService(søknadRepoMock)

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
            response.status shouldBe HttpStatusCode.Created
        }
    }

    @Test
    fun `post på soknad-endepunkt skal svare med 500 hvis lagring av søknaden feiler`() {
        mockkStatic("no.nav.tiltakspenger.soknad.api.soknad.routes.SoknadRequestMapperKt")
        coEvery { taInnSøknadSomMultipart(any(), any()) } returns Pair(mockSpørsmålsbesvarelser(), emptyList())
        val nySøknadService = mockk<NySøknadService>().also { mock ->
            coEvery { mock.nySøknad(any()) } returns KunneIkkeMottaNySøknad.KunneIkkeLagreSøknad.left()
        }

        val token = issueTestToken()
        coEvery { texasClient.introspectToken(any(), any()) } returns getGyldigTexasIntrospectionResponse(
            fnr = token.jwtClaimsSet.claims["pid"].toString(),
            acr = token.jwtClaimsSet.claims["acr"].toString(),
        )

        testApplication {
            configureTestApplication(
                texasClient = texasClient,
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
            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `post på soknad-endepunkt skal svare med 500 ved uventet feil`() {
        mockkStatic("no.nav.tiltakspenger.soknad.api.soknad.routes.SoknadRequestMapperKt")
        coEvery { taInnSøknadSomMultipart(any(), any()) } throws RuntimeException("uventet feil")

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
            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `post på soknad-endepunkt med vedlegg skal kjøre virussjekk og svare 201`() {
        mockkStatic("no.nav.tiltakspenger.soknad.api.soknad.routes.SoknadRequestMapperKt")
        val vedlegg = Vedlegg(filnavn = "fil.pdf", contentType = "application/pdf", dokument = ByteArray(1))
        coEvery { taInnSøknadSomMultipart(any(), any()) } returns Pair(mockSpørsmålsbesvarelser(), listOf(vedlegg))
        val søknadRepoMock = mockk<SøknadRepo>().also { mock ->
            coEvery { mock.lagre(any()) } returns Unit
        }

        val token = issueTestToken()
        coEvery { texasClient.introspectToken(any(), any()) } returns getGyldigTexasIntrospectionResponse(
            fnr = token.jwtClaimsSet.claims["pid"].toString(),
            acr = token.jwtClaimsSet.claims["acr"].toString(),
        )

        testApplication {
            configureTestApplication(
                texasClient = texasClient,
                avService = avService,
                nySøknadService = NySøknadService(søknadRepoMock),
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
            response.status shouldBe HttpStatusCode.Created
            coVerify(exactly = 1) { avService.gjørVirussjekkAvVedlegg(listOf(vedlegg)) }
        }
    }

    private fun issueTestToken(acr: String = "idporten-loa-high", expiry: Long = 3600): JWT {
        // expiry beholdes for kallkompatibilitet; tokenets innhold valideres ikke (introspect er mocket).
        return lagTestToken(
            mapOf(
                "acr" to acr,
                "pid" to "12345678910",
            ),
        )
    }
}
