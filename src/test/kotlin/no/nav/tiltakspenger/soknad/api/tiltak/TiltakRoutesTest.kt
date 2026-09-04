package no.nav.tiltakspenger.soknad.api.tiltak

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO
import no.nav.tiltakspenger.soknad.api.TILTAK_PATH
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.FORTROLIG
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.STRENGT_FORTROLIG
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND
import no.nav.tiltakspenger.soknad.api.testutils.jsonKlient
import no.nav.tiltakspenger.soknad.api.testutils.kometDeltakelse
import no.nav.tiltakspenger.soknad.api.testutils.leggIKøStatusForAlleForsøk
import no.nav.tiltakspenger.soknad.api.testutils.medTestApplikasjon
import no.nav.tiltakspenger.soknad.api.testutils.nyttTestFødselsnummer
import no.nav.tiltakspenger.soknad.api.testutils.pdlIdenterRespons
import no.nav.tiltakspenger.soknad.api.testutils.søkerRespons
import no.nav.tiltakspenger.soknad.api.testutils.tiltakshistorikkRespons
import no.nav.tiltakspenger.soknad.api.testutils.ukjentDeltakelsesform
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.LocalDate

/**
 * Hele kjeden ende til ende: ruta, tjenesten, begge klientene i hentingen med ekte auth og deserialisering, søknadsguarden, mappingen og tidsromsfilteret.
 * Datoene i testdataene ligger innenfor tidsrommet søknaden viser deltakelser fra, målt mot testklokka (2025-01-01).
 */
class TiltakRoutesTest {
    private val testFødselsnummer = nyttTestFødselsnummer()

    @Test
    fun `get på tiltak-endepunkt svarer med tiltak når tokenet er gyldig`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.pdlIdentTransport.leggIKøJson(pdlIdenterRespons(testFødselsnummer))
            tac.tiltakshistorikkTransport.leggIKøJson(tiltakshistorikkRespons(kometDeltakelse(fnr = testFødselsnummer)))

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.OK
            val tiltak = response.body<TiltakDto>().tiltak.single()
            tiltak.aktivitetId shouldBe "0190c9a2-2222-7000-8000-000000000002"
            // Wire-verdien er Arena-koden, ikke domenets egen tiltakstype: frontenden sender den tilbake i søknaden, og saksbehandling-api leser den med valueOf.
            tiltak.type shouldBe TiltakResponsDTO.TiltakTypeDTO.ARBFORB
            tiltak.typeNavn shouldBe "Arbeidsforberedende trening"
            tiltak.arrangør shouldBe "Testarrangør AS"
            tiltak.visningsnavn shouldBe "Arbeidsforberedende trening hos Testarrangør AS"
            tiltak.gjennomforingId shouldBe "0190c9a2-3333-7000-8000-000000000003"
            tiltak.arenaRegistrertPeriode.fra shouldBe LocalDate.of(2024, 12, 1)
            tiltak.arenaRegistrertPeriode.til shouldBe LocalDate.of(2025, 2, 28)
        }
    }

    @Test
    fun `get på tiltak-endepunkt bruker hovedenheten som arrangørnavn når kilden har begge`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.pdlIdentTransport.leggIKøJson(pdlIdenterRespons(testFødselsnummer))
            tac.tiltakshistorikkTransport.leggIKøJson(
                tiltakshistorikkRespons(
                    kometDeltakelse(fnr = testFødselsnummer, hovedenhetnavn = "Hovedenhet AS", arrangørnavn = "Underenhet AS"),
                ),
            )

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.body<TiltakDto>().tiltak.single().arrangør shouldBe "Hovedenhet AS"
        }
    }

    /**
     * `arrangør` og `gjennomforingId` er ikke-nullable på wiren.
     * Mangler kilden dem, faller de tilbake på de samme plassholderne frontenden har fått siden tiltakspenger-tiltak leverte dem.
     */
    @Test
    fun `get på tiltak-endepunkt faller tilbake på plassholdere når kilden mangler arrangørnavn`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.pdlIdentTransport.leggIKøJson(pdlIdenterRespons(testFødselsnummer))
            tac.tiltakshistorikkTransport.leggIKøJson(
                tiltakshistorikkRespons(kometDeltakelse(fnr = testFødselsnummer, arrangørnavn = null)),
            )

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.body<TiltakDto>().tiltak.single().arrangør shouldBe "Ukjent"
        }
    }

    @Test
    fun `get på tiltak-endepunkt godtar token med gammelt acr-claim`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer, acr = "Level4")
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.pdlIdentTransport.leggIKøJson(pdlIdenterRespons(testFødselsnummer))
            tac.tiltakshistorikkTransport.leggIKøJson(tiltakshistorikkRespons(kometDeltakelse(fnr = testFødselsnummer)))

            jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }.status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `get på tiltak-endepunkt fjerner arrangørnavn for søker med adressebeskyttelse`() {
        // Én kontekst per gradering: PdlClient cacher søkeroppslaget på fødselsnummer.
        listOf(FORTROLIG, STRENGT_FORTROLIG, STRENGT_FORTROLIG_UTLAND).forEach { gradering ->
            medTestApplikasjon { tac ->
                val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
                tac.pdlTransport.leggIKøJson(søkerRespons(gradering = gradering))
                tac.pdlIdentTransport.leggIKøJson(pdlIdenterRespons(testFødselsnummer))
                tac.tiltakshistorikkTransport.leggIKøJson(tiltakshistorikkRespons(kometDeltakelse(fnr = testFødselsnummer)))

                val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

                response.status shouldBe HttpStatusCode.OK
                val tiltak = response.body<TiltakDto>().tiltak.single()
                tiltak.arrangør shouldBe ""
                tiltak.visningsnavn shouldBe "Arbeidsforberedende trening"
            }
        }
    }

    @Test
    fun `get på tiltak-endepunkt henter tiltak for fødselsnummeret i pid-claimet`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.pdlIdentTransport.leggIKøJson(pdlIdenterRespons(testFødselsnummer))
            tac.tiltakshistorikkTransport.leggIKøJson(tiltakshistorikkRespons(kometDeltakelse(fnr = testFødselsnummer)))

            jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            tac.pdlTransport.mottatteKall.single().bodyTekst shouldContain testFødselsnummer
            tac.pdlIdentTransport.mottatteKall.single().bodyTekst shouldContain testFødselsnummer
            val historikkKall = tac.tiltakshistorikkTransport.mottatteKall.single()
            historikkKall.uri.toString() shouldBe "http://tiltakshistorikk.test/api/v1/historikk"
            historikkKall.bodyTekst shouldContain testFødselsnummer
            // Begge de nye klientene går med systemtoken; brukertokenet autentiserer bare inn til oss.
            historikkKall.request.headers().firstValue("Authorization").get() shouldBe "Bearer test-token"
        }
    }

    /**
     * Søknadsguarden i libs siler bort deltakelser kilden sier man ikke kan søke på.
     * Her er det statusen: `IKKE_AKTUELL` fra Komet betyr at det aldri ble noen plass.
     */
    @Test
    fun `get på tiltak-endepunkt utelater deltakelser bruker ikke kan søke på`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.pdlIdentTransport.leggIKøJson(pdlIdenterRespons(testFødselsnummer))
            tac.tiltakshistorikkTransport.leggIKøJson(
                tiltakshistorikkRespons(kometDeltakelse(fnr = testFødselsnummer, status = "IKKE_AKTUELL")),
            )

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.OK
            response.body<TiltakDto>().tiltak shouldBe emptyList()
        }
    }

    @Test
    fun `get på tiltak-endepunkt utelater deltakelser utenfor det relevante tidsrommet`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.pdlIdentTransport.leggIKøJson(pdlIdenterRespons(testFødselsnummer))
            tac.tiltakshistorikkTransport.leggIKøJson(
                tiltakshistorikkRespons(
                    kometDeltakelse(
                        fnr = testFødselsnummer,
                        fraOgMed = LocalDate.of(2023, 1, 1),
                        tilOgMed = LocalDate.of(2023, 6, 30),
                    ),
                ),
            )

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.OK
            response.body<TiltakDto>().tiltak shouldBe emptyList()
        }
    }

    /**
     * En deltakelsesform kontrakten ikke har i dag skal ikke velte oppslaget.
     * Den bæres som en ukjent kildeverdi vi logger på, mens resten av svaret går videre til bruker.
     */
    @Test
    fun `get på tiltak-endepunkt svarer med resten når kilden har en deltakelsesform vi ikke kjenner`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.pdlIdentTransport.leggIKøJson(pdlIdenterRespons(testFødselsnummer))
            tac.tiltakshistorikkTransport.leggIKøJson(
                tiltakshistorikkRespons(kometDeltakelse(fnr = testFødselsnummer), ukjentDeltakelsesform()),
            )

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.OK
            response.body<TiltakDto>().tiltak.size shouldBe 1
        }
    }

    /**
     * PDL kan svare uten brukbare identer.
     * Da hentes historikken på innsendt fnr alene — bruker får svar, og fallbacken logges som warn.
     */
    @Test
    fun `get på tiltak-endepunkt faller tilbake til innsendt fnr når PDL ikke gir identer`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.pdlIdentTransport.leggIKøJson(pdlIdenterRespons())
            tac.tiltakshistorikkTransport.leggIKøJson(tiltakshistorikkRespons(kometDeltakelse(fnr = testFødselsnummer)))

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.OK
            response.body<TiltakDto>().tiltak.size shouldBe 1
        }
    }

    @Test
    fun `get på tiltak-endepunkt svarer 401 når token mangler`() {
        medTestApplikasjon {
            jsonKlient().get(TILTAK_PATH).status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `get på tiltak-endepunkt svarer 401 for token som ikke er utstedt til oss`() {
        medTestApplikasjon {
            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer ukjent-token") }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `get på tiltak-endepunkt svarer 401 når acr-claimet mangler`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer, acr = "")

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `get på tiltak-endepunkt svarer 500 når pdl-kallet feiler`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøKast(IOException("pdl er nede"))

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `get på tiltak-endepunkt svarer 500 når identoppslaget mot PDL feiler`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøJson(søkerRespons())
            // PdlIdentklient har ingen retry, så ett køet svar dekker hele kallet.
            tac.pdlIdentTransport.leggIKøStatus(500, "pdl er nede")

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `get på tiltak-endepunkt svarer 500 når tiltakshistorikk feiler`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.pdlIdentTransport.leggIKøJson(pdlIdenterRespons(testFødselsnummer))
            // TiltakshistorikkKlient retryer inntil tre ganger, og hvert forsøk henter sitt eget svar fra køen.
            tac.tiltakshistorikkTransport.leggIKøStatusForAlleForsøk(500, "tiltakshistorikk er nede", maksForsøk = 3)

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }

    /**
     * Et svar som ikke lar seg tolke feller hele oppslaget i stedet for at rader forsvinner stille.
     * Her er det to rader med samme deltakelses-id.
     */
    @Test
    fun `get på tiltak-endepunkt svarer 500 når svaret fra tiltakshistorikk ikke kan tolkes`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.pdlTransport.leggIKøJson(søkerRespons())
            tac.pdlIdentTransport.leggIKøJson(pdlIdenterRespons(testFødselsnummer))
            tac.tiltakshistorikkTransport.leggIKøJson(
                tiltakshistorikkRespons(
                    kometDeltakelse(fnr = testFødselsnummer),
                    kometDeltakelse(fnr = testFødselsnummer),
                ),
            )

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `get på tiltak-endepunkt svarer 500 når søkeren er registrert som død i PDL`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            // Mappingen kaster på død søker; ruta skal ta det som en uventet feil og svare 500.
            tac.pdlTransport.leggIKøJson(søkerRespons(død = true))

            val response = jsonKlient().get(TILTAK_PATH) { header("Authorization", "Bearer $token") }

            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }
}
