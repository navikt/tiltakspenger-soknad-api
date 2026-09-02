package no.nav.tiltakspenger.soknad.api.tiltak.skygge

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO
import no.nav.tiltakspenger.libs.tiltak.TiltakshistorikkDTO
import no.nav.tiltakspenger.soknad.api.TILTAK_PATH
import no.nav.tiltakspenger.soknad.api.testutils.TestApplicationContext
import no.nav.tiltakspenger.soknad.api.testutils.jsonKlient
import no.nav.tiltakspenger.soknad.api.testutils.medTestApplikasjon
import no.nav.tiltakspenger.soknad.api.testutils.nyttTestFødselsnummer
import no.nav.tiltakspenger.soknad.api.testutils.søkerRespons
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakDto
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Hele kjeden ende til ende: ruta, tjenesten, cache-sjekken, sidekallet, begge de nye klientene med ekte auth og deserialisering, mappingen og sammenligningen.
 * Skyggen står av i drift til vi har tilgang hos Team Valp, så dette er stedet den faktisk kjører — og det som fanger en wiring som er røket.
 *
 * Datoene ligger innenfor tidsrommet søknaden viser deltakelser fra, målt mot testklokka (2025-01-01).
 */
class TiltaksdeltakelseSkyggeRouteTest {
    private val testFødselsnummer = nyttTestFødselsnummer()
    private val deltakelseId = "0190c9a2-2222-7000-8000-000000000002"
    private val gjennomføringId = "0190c9a2-3333-7000-8000-000000000003"

    @Test
    fun `kall mot tiltak-ruta kjører skyggen mot begge kildene og finner ingen avvik`() {
        val tac = TestApplicationContext(skyggePåslag = true)
        medTestApplikasjon(tac) {
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.tiltakTransport.leggIKøJson(listOf(gammelRad()))
            tac.pdlIdentTransport.leggIKøJson(pdlIdenterJson)
            tac.tiltakshistorikkTransport.leggIKøJson(historikkJson)

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }
            tac.ventPåSkyggen()

            // Svaret til bruker er uendret: skyggen er ren sideeffekt.
            response.status shouldBe HttpStatusCode.OK
            response.body<TiltakDto>().tiltak.single().aktivitetId shouldBe deltakelseId

            tac.pdlIdentTransport.mottatteKall.size shouldBe 1
            tac.tiltakshistorikkTransport.mottatteKall.size shouldBe 1
            tac.metricsCollector.tiltaksdeltakelseSkyggeKjøringer.labels(UTFALL_LIKT).get() shouldBe 1.0
        }
    }

    /**
     * Andre oppslag for samme person svares fra klientens cache, og et cachet svar kan være opptil en time gammelt.
     * Da måler vi tid i stedet for mapping, så skyggen skal la være å kjøre.
     */
    @Test
    fun `et cachet svar fra gammel vei gir ingen ny skyggekjøring`() {
        val tac = TestApplicationContext(skyggePåslag = true)
        medTestApplikasjon(tac) {
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.tiltakTransport.leggIKøJson(listOf(gammelRad()))
            tac.pdlIdentTransport.leggIKøJson(pdlIdenterJson)
            tac.tiltakshistorikkTransport.leggIKøJson(historikkJson)

            jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }
            jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }
            tac.ventPåSkyggen()

            // Ingen ekstra svar er køet, så et nytt skyggekall ville feilet på tom kø — antallet er beviset.
            tac.tiltakshistorikkTransport.mottatteKall.size shouldBe 1
            tac.metricsCollector.tiltaksdeltakelseSkyggeKjøringer.labels(UTFALL_LIKT).get() shouldBe 1.0
        }
    }

    /** Raden slik tiltakspenger-tiltak leverer den, med verdier som matcher Komet-raden under felt for felt. */
    private fun gammelRad() = TiltakshistorikkDTO(
        id = deltakelseId,
        gjennomforing = TiltakshistorikkDTO.GjennomforingDTO(
            id = gjennomføringId,
            visningsnavn = "Arbeidsforberedende trening hos Arrangør AS",
            arrangornavn = "Arrangør AS",
            typeNavn = "Arbeidsforberedende trening",
            arenaKode = TiltakResponsDTO.TiltakTypeDTO.ARBFORB,
            deltidsprosent = null,
        ),
        deltakelseFom = LocalDate.of(2024, 12, 1),
        deltakelseTom = LocalDate.of(2025, 2, 28),
        deltakelseStatus = TiltakResponsDTO.DeltakerStatusDTO.DELTAR,
        antallDagerPerUke = 3f,
        deltakelseProsent = 60f,
        kilde = TiltakshistorikkDTO.Kilde.KOMET,
    )

    private val pdlIdenterJson =
        """{"data": {"hentIdenter": {"identer": [{"ident": "$testFødselsnummer"}]}}, "errors": null}"""

    private val historikkJson = """
        {
          "historikk": [
            {
              "type": "TeamKometDeltakelse",
              "norskIdent": "$testFødselsnummer",
              "startDato": "2024-12-01",
              "sluttDato": "2025-02-28",
              "id": "$deltakelseId",
              "tittel": "Arbeidsforberedende trening hos Arrangør AS",
              "status": { "type": "DELTAR", "aarsak": null, "opprettetDato": "2024-11-01T09:30:00" },
              "tiltakstype": { "tiltakskode": "ARBEIDSFORBEREDENDE_TRENING", "navn": "Arbeidsforberedende trening" },
              "gjennomforing": { "id": "$gjennomføringId", "navn": null, "deltidsprosent": null },
              "arrangor": { "hovedenhet": null, "underenhet": { "organisasjonsnummer": "912345678", "navn": "Arrangør AS" } },
              "deltidsprosent": 60.0,
              "dagerPerUke": 3.0
            }
          ],
          "meldinger": []
        }
    """.trimIndent()
}
