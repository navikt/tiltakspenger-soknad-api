package no.nav.tiltakspenger.soknad.api.antivirus

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import org.junit.jupiter.api.Test

/** Bruker reell [ClamAvClient] med [FakeHttpTransport], slik at hele klient-pipelinen kjører sammen med servicen. */
internal class AvServiceTest {
    private val vedlegg = nonEmptyListOf(
        Vedlegg(filnavn = "fil.pdf", contentType = "application/pdf", dokument = ByteArray(1)),
    )

    private fun avService(transport: FakeHttpTransport) = AvService(
        clamAvClient = ClamAvClient(avEndpoint = "http://clamav/scan", clock = fixedClock, transport = transport),
        sikkerlogg = Sikkerlogg,
    )

    @Test
    fun `rene vedlegg passerer virussjekken`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""[{"Filename":"fil.pdf","Result":"OK"}]""")

        avService(transport).gjørVirussjekkAvVedlegg(vedlegg).getOrFail() shouldBe Unit
    }

    @Test
    fun `funn av skadevare gir SkadevareFunnet`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""[{"Filename":"fil.pdf","Result":"FOUND"}]""")

        avService(transport).gjørVirussjekkAvVedlegg(vedlegg).leftOrNull()!! shouldBe VirussjekkFeil.SkadevareFunnet
    }

    @Test
    fun `feil under skanning gir SkanningFeilet med filnavnene, og vinner over et samtidig virusfunn`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""[{"Filename":"fil.pdf","Result":"ERROR"},{"Filename":"annen.pdf","Result":"FOUND"}]""")

        avService(transport).gjørVirussjekkAvVedlegg(vedlegg).leftOrNull()!! shouldBe
            VirussjekkFeil.SkanningFeilet(nonEmptyListOf("fil.pdf"))
    }

    @Test
    fun `feilet kall til ClamAV gir KallFeilet`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503, body = "clamav er nede")

        avService(transport).gjørVirussjekkAvVedlegg(vedlegg).leftOrNull()!!
            .shouldBeInstanceOf<VirussjekkFeil.KallFeilet>()
            .feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 503
    }
}
