package no.nav.tiltakspenger.soknad.api.tiltak

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.random
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import org.junit.jupiter.api.Test

/** Bruker reell [TiltakspengerTiltakClient] med [FakeHttpTransport], slik at hele klient-pipelinen kjører sammen med servicen. */
internal class TiltakServiceTest {
    private fun tiltakService(transport: FakeHttpTransport): TiltakService {
        val texasClient = mockk<TexasHttpClient>().also {
            coEvery { it.exchangeToken(any(), any(), any()) } returns
                AccessToken("obo-token", fixedClock.instant().plusSeconds(3600))
        }
        return TiltakService(
            tiltakspengerTiltakClient = TiltakspengerTiltakClient(
                tiltakspengerTiltakEndpoint = "http://tiltak",
                clock = fixedClock,
                tiltakspengerTiltakScope = "scope",
                texasClient = texasClient,
                transport = transport,
            ),
            clock = fixedClock,
            sikkerlogg = Sikkerlogg,
        )
    }

    @Test
    fun `tiltaksarrangør maskeres når maskerArrangørnavn=true`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(tiltakshistorikkJson())

        val tiltak = tiltakService(transport).hentTiltak("subjectToken", Fnr.random(), maskerArrangørnavn = true).getOrFail()

        tiltak.first().arrangør shouldBe ""
    }

    @Test
    fun `man får all informasjon om tiltaket når maskerArrangørnavn=false`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(tiltakshistorikkJson(arrangør = "Arrangør AS"))

        val tiltak = tiltakService(transport).hentTiltak("subjectToken", Fnr.random(), maskerArrangørnavn = false).getOrFail()

        tiltak.first().arrangør shouldBe "Arrangør AS"
    }

    @Test
    fun `ved feil mot tiltakspenger-tiltak forplanter HttpKlientError seg ut til kalleren`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(404, body = "finnes ikke")

        val feil = tiltakService(transport).hentTiltak("subjectToken", Fnr.random(), maskerArrangørnavn = false).leftOrNull()!!

        feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 404
    }

    private fun tiltakshistorikkJson(arrangør: String = "Arrangør AS") = """
        [
          {
            "id": "123456",
            "gjennomforing": {
              "id": "123456",
              "arenaKode": "ABIST",
              "typeNavn": "typenavn",
              "arrangornavn": "$arrangør",
              "deltidsprosent": 100.0,
              "visningsnavn": "Typenavn hos $arrangør"
            },
            "deltakelseFom": null,
            "deltakelseTom": null,
            "deltakelseStatus": "DELTAR",
            "antallDagerPerUke": null,
            "deltakelseProsent": null,
            "kilde": "KOMET"
          }
        ]
    """.trimIndent()
}
