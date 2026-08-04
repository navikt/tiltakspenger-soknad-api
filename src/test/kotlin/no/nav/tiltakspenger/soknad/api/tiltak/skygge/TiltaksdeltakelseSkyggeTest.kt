package no.nav.tiltakspenger.soknad.api.tiltak.skygge

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.fixedClockAt
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO
import no.nav.tiltakspenger.libs.tiltak.TiltakshistorikkDTO
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.pdl.PdlIdentklient
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.TiltakshistorikkHenter
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.TiltakshistorikkKlient
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import no.nav.tiltakspenger.soknad.api.testutils.TestTokenProvider
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Skyggen kjøres mot den ekte klientpipelinen over [FakeHttpTransport], slik at auth, statusregler og deserialisering er med.
 * Klokka står 2024-03-01, som er innenfor tidsrommet søknaden viser deltakelsene i testdataen fra.
 */
class TiltaksdeltakelseSkyggeTest {
    private val testFødselsnummer = "12345678901"
    private val fnr = Fnr.fromString(testFødselsnummer)
    private val correlationId = CorrelationId("test-kall-id")
    private val skyggeklokke = fixedClockAt(LocalDate.of(2024, 3, 1))

    /**
     * Testklassen deler instans mellom testene (`per_class`), så alt som muteres — transportkøer og tellere — bygges per test.
     */
    private inner class Oppsett(påslag: Boolean, scope: CoroutineScope) {
        val pdlTransport = FakeHttpTransport()
        val historikkTransport = FakeHttpTransport()
        val metricsCollector = MetricsCollector(CollectorRegistry())

        val skygge = TiltaksdeltakelseSkygge(
            tiltakshistorikkHenter = TiltakshistorikkHenter(
                tiltakshistorikkKlient = TiltakshistorikkKlient(
                    baseUrl = "http://tiltakshistorikk.test",
                    clock = fixedClock,
                    authTokenProvider = TestTokenProvider(),
                    transport = historikkTransport,
                ),
                pdlIdentklient = PdlIdentklient(
                    baseUrl = "http://pdl.test",
                    clock = fixedClock,
                    authTokenProvider = TestTokenProvider(),
                    transport = pdlTransport,
                ),
                clock = fixedClock,
            ),
            metricsCollector = metricsCollector,
            skyggescope = scope,
            clock = skyggeklokke,
            sikkerlogg = Sikkerlogg,
            påslag = påslag,
        )

        /** Ett vellykket oppslag i ny vei: identene fra PDL og én Arena-rad fra tiltakshistorikk. */
        fun køSvar(historikk: String = historikkJson) {
            pdlTransport.leggIKøJson(pdlJson)
            historikkTransport.leggIKøJson(historikk)
        }
    }

    private fun sidekallsscope(scheduler: TestCoroutineScheduler) = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))

    /**
     * Bryteren står av til vi har rollen `tiltakshistorikk-read` hos Team Valp.
     * Da skal skyggen heller ikke kalle noen av kildene — en app uten tilgang skal ikke merke at koden finnes.
     */
    @Test
    fun `en avslått skygge rører hverken PDL eller tiltakshistorikk`() = runTest {
        val oppsett = Oppsett(påslag = false, scope = sidekallsscope(testScheduler))

        oppsett.skygge.kjørISkyggen(fnr, listOf(gammelRad()), correlationId)
        advanceUntilIdle()

        oppsett.pdlTransport.mottatteKall.shouldBeEmpty()
        oppsett.historikkTransport.mottatteKall.shouldBeEmpty()
        oppsett.metricsCollector.tiltaksdeltakelseSkyggeKjøringer.labels(UTFALL_LIKT).get() shouldBe 0.0
    }

    @Test
    fun `sidekallet kjører sammenligningen når skyggen er på`() = runTest {
        val oppsett = Oppsett(påslag = true, scope = sidekallsscope(testScheduler))
        oppsett.køSvar()

        oppsett.skygge.kjørISkyggen(fnr, listOf(gammelRad()), correlationId)
        advanceUntilIdle()

        oppsett.metricsCollector.tiltaksdeltakelseSkyggeKjøringer.labels(UTFALL_LIKT).get() shouldBe 1.0
        oppsett.metricsCollector.tiltaksdeltakelseSkyggeSammenlignedeDeltakelser.get() shouldBe 1.0
        oppsett.pdlTransport.mottatteKall.size shouldBe 1
        oppsett.historikkTransport.mottatteKall.size shouldBe 1
    }

    @Test
    fun `feltavvik telles per felt, og kjøringen merkes som avvik`() = runTest {
        val oppsett = Oppsett(påslag = true, scope = this)
        oppsett.køSvar()

        oppsett.skygge.sammenlign(fnr, listOf(gammelRad(typeNavn = "Et annet navn")), correlationId)

        oppsett.metricsCollector.tiltaksdeltakelseSkyggeFeltavvik.labels("tiltakstypenavn").get() shouldBe 1.0
        oppsett.metricsCollector.tiltaksdeltakelseSkyggeKjøringer.labels(UTFALL_AVVIK).get() shouldBe 1.0
        oppsett.metricsCollector.tiltaksdeltakelseSkyggeKjøringer.labels(UTFALL_LIKT).get() shouldBe 0.0
    }

    @Test
    fun `radene bare én av veiene har telles med hver sin klassifisering`() = runTest {
        val oppsett = Oppsett(påslag = true, scope = this)
        oppsett.køSvar()

        oppsett.skygge.sammenlign(fnr, listOf(gammelRad(id = "TA9999999")), correlationId)

        oppsett.metricsCollector.tiltaksdeltakelseSkyggeKunIGammel.labels(Fraværsgrunn.MANGLER_I_NY).get() shouldBe 1.0
        oppsett.metricsCollector.tiltaksdeltakelseSkyggeKunINy.labels("Arena:GJENNOMFORES").get() shouldBe 1.0
    }

    /**
     * En vei som feiler ofte skal ikke kunne se ut som en vei uten avvik.
     * Feilen er allerede logget i hente-tjenesten, så skyggen teller den bare.
     */
    @Test
    fun `et feilende oppslag i ny vei telles for seg`() = runTest {
        val oppsett = Oppsett(påslag = true, scope = this)
        oppsett.pdlTransport.leggIKøJson(pdlJson)
        oppsett.historikkTransport.leggIKøStatus(403, body = "mangler tilgang")

        oppsett.skygge.sammenlign(fnr, listOf(gammelRad()), correlationId)

        oppsett.metricsCollector.tiltaksdeltakelseSkyggeKjøringer.labels(UTFALL_NY_VEI_FEILET).get() shouldBe 1.0
    }

    /**
     * To tomme lister er trivielt like, og ville tidligere blitt talt som enighet.
     * Da kunne skyggen meldt «alt stemmer» uten å ha sammenlignet en eneste rad — derfor har det tilfellet sin egen merkelapp.
     */
    @Test
    fun `et oppslag uten deltakelser telles som tomt, ikke som enighet`() = runTest {
        val oppsett = Oppsett(påslag = true, scope = this)
        oppsett.pdlTransport.leggIKøJson(pdlJson)
        oppsett.historikkTransport.leggIKøJson("""{"historikk": [], "meldinger": []}""")

        oppsett.skygge.sammenlign(fnr, gammel = emptyList(), correlationId = correlationId)

        oppsett.metricsCollector.tiltaksdeltakelseSkyggeKjøringer.labels(UTFALL_TOMT).get() shouldBe 1.0
        oppsett.metricsCollector.tiltaksdeltakelseSkyggeKjøringer.labels(UTFALL_LIKT).get() shouldBe 0.0
        oppsett.metricsCollector.tiltaksdeltakelseSkyggeSammenlignedeDeltakelser.get() shouldBe 0.0
    }

    /**
     * Feiler PDL-kallet, får vi aldri slått opp historikken.
     * Skilles fra et feilende tiltakshistorikk-kall, siden det er to ulike naboer som er nede.
     */
    @Test
    fun `et feilende identoppslag telles og logges uten å felle noe annet`() = runTest {
        val oppsett = Oppsett(påslag = true, scope = this)
        oppsett.pdlTransport.leggIKøStatus(500, body = "pdl er nede")

        oppsett.skygge.sammenlign(fnr, listOf(gammelRad()), correlationId)

        oppsett.metricsCollector.tiltaksdeltakelseSkyggeKjøringer.labels(UTFALL_NY_VEI_FEILET).get() shouldBe 1.0
        oppsett.historikkTransport.mottatteKall.shouldBeEmpty()
    }

    /**
     * Et svar vi ikke kan tolke feller hele oppslaget med vilje, framfor at rader forsvinner stille.
     * Her er `norskIdent` en annen person enn vi spurte for — den alvorligste varianten, siden det kan være en annens data.
     */
    @Test
    fun `et utolkbart svar telles som feilet vei`() = runTest {
        val oppsett = Oppsett(påslag = true, scope = this)
        oppsett.pdlTransport.leggIKøJson(pdlJson)
        oppsett.historikkTransport.leggIKøJson(historikkJson.replace(testFødselsnummer, "10987654321"))

        oppsett.skygge.sammenlign(fnr, listOf(gammelRad()), correlationId)

        oppsett.metricsCollector.tiltaksdeltakelseSkyggeKjøringer.labels(UTFALL_NY_VEI_FEILET).get() shouldBe 1.0
    }

    /**
     * PDL-fallbacken var tidligere en `error` inne i libs og dermed usynlig for konsumenten.
     * Nå telles den: oppslaget lykkes, men på færre identer enn personen kan ha hatt, så deltakelser kan mangle uten at noe feilet.
     */
    @Test
    fun `fallback til innsendt fnr telles selv om oppslaget lykkes`() = runTest {
        val oppsett = Oppsett(påslag = true, scope = this)
        oppsett.pdlTransport.leggIKøJson("""{"data": {"hentIdenter": {"identer": []}}, "errors": null}""")
        oppsett.historikkTransport.leggIKøJson(historikkJson)

        oppsett.skygge.sammenlign(fnr, listOf(gammelRad()), correlationId)

        oppsett.metricsCollector.tiltaksdeltakelseSkyggeIdentfallback.labels("FantIngenIdenter").get() shouldBe 1.0
        // Oppslaget lykkes likevel, så sammenligningen kjøres som vanlig.
        oppsett.metricsCollector.tiltaksdeltakelseSkyggeKjøringer.labels(UTFALL_LIKT).get() shouldBe 1.0
    }

    /**
     * Ukjente kildeverdier og ufullstendige svar er funn gammel vei aldri kunne fortalt oss om.
     * De telles, men gjør ikke kjøringen til en avvikskjøring.
     */
    @Test
    fun `ukjente kildeverdier og manglende kilder telles uten å bli avvik`() = runTest {
        val oppsett = Oppsett(påslag = true, scope = this)
        oppsett.køSvar(historikkMedMeldingerJson)

        oppsett.skygge.sammenlign(fnr, listOf(gammelRad()), correlationId)

        oppsett.metricsCollector.tiltaksdeltakelseSkyggeUkjentKildeverdi.labels("melding fra tiltakshistorikk").get() shouldBe 1.0
        oppsett.metricsCollector.tiltaksdeltakelseSkyggeManglendeKilde.labels("TeamTiltak").get() shouldBe 1.0
        oppsett.metricsCollector.tiltaksdeltakelseSkyggeKjøringer.labels(UTFALL_LIKT).get() shouldBe 1.0
    }

    /**
     * Siste skanse: uansett hva som går galt i sidekallet, skal ingenting komme ut av det.
     * Her har transporten tom kø, altså en feil ingen har tenkt på — og den skal dø i sikkerhetsnettet.
     */
    @Test
    fun `en uventet feil i sidekallet slipper aldri ut`() = runTest {
        val oppsett = Oppsett(påslag = true, scope = sidekallsscope(testScheduler))

        shouldNotThrowAny {
            oppsett.skygge.kjørISkyggen(fnr, listOf(gammelRad()), correlationId)
        }
        advanceUntilIdle()

        oppsett.metricsCollector.tiltaksdeltakelseSkyggeKjøringer.labels(UTFALL_LIKT).get() shouldBe 0.0
    }

    private fun gammelRad(
        id: String = "TA142536",
        typeNavn: String = "Oppfølging",
    ) = TiltakshistorikkDTO(
        id = id,
        gjennomforing = TiltakshistorikkDTO.GjennomforingDTO(
            // Tom, som dagens mapper setter den for Arena.
            id = "",
            visningsnavn = "Oppfølging hos Arrangør AS",
            arrangornavn = "Arrangør AS",
            typeNavn = typeNavn,
            arenaKode = TiltakResponsDTO.TiltakTypeDTO.INDOPPFAG,
            deltidsprosent = null,
        ),
        deltakelseFom = LocalDate.of(2024, 1, 1),
        deltakelseTom = LocalDate.of(2024, 6, 30),
        deltakelseStatus = TiltakResponsDTO.DeltakerStatusDTO.DELTAR,
        antallDagerPerUke = 5f,
        deltakelseProsent = 100f,
        kilde = TiltakshistorikkDTO.Kilde.ARENA,
    )

    private val pdlJson = """{"data": {"hentIdenter": {"identer": [{"ident": "$testFødselsnummer"}]}}, "errors": null}"""

    private val arenaRadJson = """
        {
          "type": "ArenaDeltakelse",
          "norskIdent": "$testFødselsnummer",
          "startDato": "2024-01-01",
          "sluttDato": "2024-06-30",
          "id": "019018e5-6461-74a0-9d66-70d0bf3d0b8b",
          "tittel": "Oppfølging hos Arrangør AS",
          "arenaId": 142536,
          "status": "GJENNOMFORES",
          "tiltakstype": { "tiltakskode": "INDOPPFAG", "navn": "Oppfølging" },
          "gjennomforing": { "id": "0190c9a2-1111-7000-8000-000000000001", "navn": null, "deltidsprosent": 50.0 },
          "arrangor": { "hovedenhet": null, "underenhet": { "organisasjonsnummer": "912345678", "navn": "Arrangør AS" } },
          "deltidsprosent": 100.0,
          "dagerPerUke": 5.0
        }
    """.trimIndent()

    private val historikkJson = """{"historikk": [$arenaRadJson], "meldinger": []}"""

    private val historikkMedMeldingerJson =
        """{"historikk": [$arenaRadJson], "meldinger": ["MANGLER_HISTORIKK_FRA_TEAM_TILTAK", "HELT_NY_MELDING"]}"""
}
