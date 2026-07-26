package no.nav.tiltakspenger.soknad.api.dokarkiv

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.soknad.api.soknad.validering.søknad
import no.nav.tiltakspenger.soknad.api.testutils.testTokenProvider
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import org.junit.jupiter.api.Test

internal class DokarkivClientTest {
    private val journalpostId = JournalpostId("1")

    private fun klient(baseUrl: String, transport: HttpTransport? = null) = if (transport == null) {
        DokarkivClient(baseUrl = baseUrl, clock = fixedClock, authTokenProvider = testTokenProvider)
    } else {
        DokarkivClient(baseUrl = baseUrl, clock = fixedClock, authTokenProvider = testTokenProvider, transport = transport)
    }

    @Test
    fun `oppretter journalpost med default HttpKlient-oppsett`() {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                post(urlEqualTo("/$DOKARKIV_PATH?forsoekFerdigstill=false")).willReturn(
                    aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(svar(ferdigstilt = false)),
                ),
            )

            runTest {
                klient(wiremock.baseUrl()).opprettJournalpost(journalpostRequest(), callId = "123").getOrFail() shouldBe journalpostId
            }
        }
    }

    @Test
    fun `uten saksnummer ber vi ikke om ferdigstilling, og en ikke-ferdigstilt journalpost er da grei`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(svar(ferdigstilt = false))

        klient("http://dokarkiv", transport).opprettJournalpost(journalpostRequest(), callId = "123").getOrFail() shouldBe journalpostId

        val kall = transport.mottatteKall.single()
        kall.metode shouldBe "POST"
        kall.uri.toString() shouldBe "http://dokarkiv/$DOKARKIV_PATH?forsoekFerdigstill=false"
        kall.request.headers().firstValue("Nav-Callid").get() shouldBe "123"
        kall.request.headers().firstValue("X-Correlation-ID").get() shouldBe "123"
        kall.request.headers().firstValue("Authorization").get() shouldBe "Bearer test-token"
    }

    @Test
    fun `med saksnummer ber vi om ferdigstilling, og en ferdigstilt journalpost gir journalpostId`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(svar(ferdigstilt = true))

        klient("http://dokarkiv", transport)
            .opprettJournalpost(journalpostRequest(saksnummer = "202501011001"), callId = "123")
            .getOrFail() shouldBe journalpostId

        transport.mottatteKall.single().uri.toString() shouldBe "http://dokarkiv/$DOKARKIV_PATH?forsoekFerdigstill=true"
    }

    @Test
    fun `opprettet, men ikke ferdigstilt, journalpost gir IkkeFerdigstilt med journalpostIden`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(svar(ferdigstilt = false))

        val feil = klient("http://dokarkiv", transport)
            .opprettJournalpost(journalpostRequest(saksnummer = "202501011001"), callId = "123")
            .leftOrNull()!!

        feil shouldBe KunneIkkeJournalføre.IkkeFerdigstilt(journalpostId)
    }

    @Test
    fun `409 Conflict betyr at journalposten allerede finnes, og vi leser journalpostIden fra dedupliseringssvaret`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(409, body = svar(ferdigstilt = false))

        klient("http://dokarkiv", transport).opprettJournalpost(journalpostRequest(), callId = "123").getOrFail() shouldBe journalpostId
    }

    @Test
    fun `409 med en body vi ikke kan lese forblir en feil`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(409, body = """{"noe":"helt annet"}""")

        val feil = klient("http://dokarkiv", transport).opprettJournalpost(journalpostRequest(), callId = "123").leftOrNull()!!

        feil.shouldBeInstanceOf<KunneIkkeJournalføre.KallFeilet>()
            .feil.shouldBeInstanceOf<HttpKlientError.DeserializationError>()
    }

    @Test
    fun `feilstatus gir KallFeilet med lesbar responsbody`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(400, body = """{"feilmelding":"ugyldig journalpost"}""")

        val feil = klient("http://dokarkiv", transport).opprettJournalpost(journalpostRequest(), callId = "123").leftOrNull()!!

        val uventetStatus = feil.shouldBeInstanceOf<KunneIkkeJournalføre.KallFeilet>()
            .feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        uventetStatus.statusCode shouldBe 400
        uventetStatus.body shouldContain "ugyldig journalpost"
    }

    @Test
    fun `retryer serverfeil fire ganger totalt, som den gamle ktor-klienten`() = runTest {
        val transport = FakeHttpTransport()
        repeat(4) { transport.leggIKøStatus(503, body = "nede") }

        klient("http://dokarkiv", transport).opprettJournalpost(journalpostRequest(), callId = "123").leftOrNull()!!

        transport.mottatteKall.size shouldBe 4
    }

    private fun journalpostRequest(saksnummer: String? = null) = JournalpostRequest.from(
        fnr = "ident",
        søknad = søknad(),
        pdf = "dette er pdf innholdet".toByteArray(),
        vedlegg = listOf(
            Vedlegg(filnavn = "filnavnVedlegg", contentType = "application/pdf", dokument = "vedleggInnhold".toByteArray()),
        ),
        saksnummer = saksnummer,
    )

    private fun svar(ferdigstilt: Boolean) = """{"journalpostId":"$journalpostId","journalpostferdigstilt":$ferdigstilt}"""
}
