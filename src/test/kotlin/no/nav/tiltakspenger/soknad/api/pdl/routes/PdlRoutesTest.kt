package no.nav.tiltakspenger.soknad.api.pdl.routes

import com.nimbusds.jwt.JWT
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson3.JacksonConverter
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.json.objectMapper
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.libs.texas.client.TexasIntrospectionResponse
import no.nav.tiltakspenger.soknad.api.configureTestApplication
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.pdl.Person
import no.nav.tiltakspenger.soknad.api.pdl.routes.dto.PersonDTO
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakService
import no.nav.tiltakspenger.soknad.api.util.getGyldigTexasIntrospectionResponse
import no.nav.tiltakspenger.soknad.api.util.lagTestToken
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class PdlRoutesTest {
    private val texasClient = mockk<TexasHttpClient>()
    private val pdlService = mockk<PdlService>()
    private val tiltakservice = mockk<TiltakService>(relaxed = true)

    private val testFødselsnummer = "12345678910"

    private val mockedPerson = Person(
        fnr = Fnr.fromString(testFødselsnummer),
        fornavn = "foo",
        etternavn = "bar",
        mellomnavn = "baz",
        fødselsdato = LocalDate.MAX,
        adressebeskyttelseGradering = AdressebeskyttelseGradering.UGRADERT,
        erDød = false,
        geografiskTilknytning = "1122",
    )

    @BeforeEach
    fun setupMocks() {
        clearMocks(texasClient, pdlService)
        coEvery { pdlService.hentPersonaliaMedBarn(any(), any(), any()) } returns mockedPerson.toPersonDTO()
    }

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
                    register(ContentType.Application.Json, JacksonConverter(objectMapper))
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
                response.status shouldBe HttpStatusCode.Companion.OK
                val body: PersonDTO = response.body()
                body.fornavn shouldBe mockedPerson.fornavn
                body.etternavn shouldBe mockedPerson.etternavn
                body.mellomnavn shouldBe mockedPerson.mellomnavn
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
                    register(ContentType.Application.Json, JacksonConverter(objectMapper))
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
                    register(ContentType.Application.Json, JacksonConverter(objectMapper))
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
                response.status shouldBe HttpStatusCode.Companion.Unauthorized
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
                    register(ContentType.Application.Json, JacksonConverter(objectMapper))
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
                response.status shouldBe HttpStatusCode.Companion.Unauthorized
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
                    register(ContentType.Application.Json, JacksonConverter(objectMapper))
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
                response.status shouldBe HttpStatusCode.Companion.Unauthorized
            }
        }
    }

    private fun issueTestToken(
        issuer: String = "tokendings",
        claims: Map<String, String> = mapOf(
            "acr" to "idporten-loa-high",
            "pid" to testFødselsnummer,
        ),
    ): JWT {
        // issuer beholdes for kallkompatibilitet; tokenets innhold valideres ikke (introspect er mocket).
        return lagTestToken(claims)
    }
}
