package no.nav.tiltakspenger.soknad.api.tiltak.skygge

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO
import no.nav.tiltakspenger.libs.tiltak.TiltakshistorikkDTO
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Arenastatus
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltaksdeltakelse
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltaksdeltakelser
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakshistorikk
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakshistorikkmelding
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakshistorikkmeldinger
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakskilde
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakstype
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.UkjentDeltakelsesform
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.UkjenteDeltakelsesformer
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.testSlutt
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.testStart
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.testdeltakelse
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Fasiten er dagens søknadsflate: lista `/tokenx/tiltakshistorikk` gir, silt gjennom tidsromsfilteret i søknaden.
 * Testene her pinner klassifiseringen, ikke domenereglene — hvilke statuser som gir søknadsrett er domene-testet i libs.
 */
class SkyggesammenligningTest {
    private val iDato = LocalDate.of(2026, 4, 1)

    @Test
    fun `to veier som gir samme utvalg og samme verdier har ingen avvik`() {
        val utfall = sammenlign(gammel = listOf(gammelRad()), ny = listOf(testdeltakelse()))

        utfall.harAvvik shouldBe false
        utfall.antallFelles shouldBe 1
        utfall.feltavvik.shouldBeEmpty()
        utfall.kunIGammel.shouldBeEmpty()
        utfall.kunINy.shouldBeEmpty()
    }

    @Test
    fun `hvert felt søknaden bærer videre sammenlignes for seg`() {
        val utfall = sammenlign(
            gammel = listOf(
                gammelRad(
                    deltakelseFom = testStart.minusDays(1),
                    deltakelseTom = testStart.plusDays(1),
                    arenaKode = TiltakResponsDTO.TiltakTypeDTO.ARBTREN,
                    typeNavn = "Et annet navn",
                    visningsnavn = "En annen tittel",
                    arrangornavn = "En annen arrangør",
                    antallDagerPerUke = 5f,
                    deltakelseProsent = 100f,
                ),
            ),
            ny = listOf(testdeltakelse()),
        )

        utfall.harAvvik shouldBe true
        utfall.feltavvik.map { it.felt } shouldContainExactlyInAnyOrder listOf(
            "fraOgMed",
            "tilOgMed",
            "tiltakstype",
            "tiltakstypenavn",
            "tittel",
            "arrangørnavn",
            "dagerPerUke",
            "deltakelsesprosent",
        )
    }

    @Test
    fun `feltavviket bærer begge verdiene, slik at de kan leses i sikkerlogg`() {
        val utfall = sammenlign(
            gammel = listOf(gammelRad(typeNavn = "Oppfølging etter gammel modell")),
            ny = listOf(testdeltakelse()),
        )

        utfall.feltavvik.single() shouldBe Feltavvik(
            id = "TA1234567",
            felt = "tiltakstypenavn",
            gammel = "Oppfølging etter gammel modell",
            ny = "Oppfølging",
        )
    }

    /**
     * Dagens mapper kaster Arenas gjennomføring og setter tom streng, mens ny vei bærer den.
     * Avviket er tilsiktet og ville truffet hver eneste Arena-rad, så det telles ikke — men en gjennomføring gammel vei faktisk bar skal fortsatt stemme.
     */
    @Test
    fun `gjennomføringsid sammenlignes bare når gammel vei bar en`() {
        val medTomGjennomføring = sammenlign(
            gammel = listOf(gammelRad(gjennomforingId = "")),
            ny = listOf(testdeltakelse()),
        )
        medTomGjennomføring.feltavvik.shouldBeEmpty()

        val medGjennomføring = sammenlign(
            gammel = listOf(gammelRad(gjennomforingId = "gjennomforing-1")),
            ny = listOf(testdeltakelse()),
        )
        medGjennomføring.feltavvik.single().felt shouldBe "gjennomføringId"
    }

    @Test
    fun `en tiltakskode gammel vei ikke kan oversette gir avvik i stedet for å kaste`() {
        val utfall = sammenlign(
            gammel = listOf(gammelRad(arenaKode = TiltakResponsDTO.TiltakTypeDTO.ABOPPF)),
            ny = listOf(testdeltakelse()),
        )

        utfall.feltavvik.single() shouldBe Feltavvik(id = "TA1234567", felt = "tiltakstype", gammel = null, ny = "OPPFØLGING")
    }

    @Test
    fun `en deltakelse bare ny vei tar med havner i egen bøtte, merket med kildens status`() {
        val utfall = sammenlign(gammel = emptyList(), ny = listOf(testdeltakelse()))

        utfall.kunINy shouldContainExactly listOf(KunINy(id = "TA1234567", kildestatus = "Komet:DELTAR"))
        utfall.kunIGammel.shouldBeEmpty()
        utfall.antallFelles shouldBe 0
    }

    @Test
    fun `en rad ny vei aldri fikk se er den alvorligste — den mangler helt`() {
        val utfall = sammenlign(gammel = listOf(gammelRad()), ny = emptyList())

        utfall.kunIGammel shouldContainExactly listOf(KunIGammel(id = "TA1234567", grunn = Fraværsgrunn.MANGLER_I_NY))
    }

    /**
     * Arenas `IKKE_MOTT` gir søknadsrett i dag (den normaliseres til «Avbrutt»), men ikke i ny modell.
     * Avviket er kjent og venter på fag; skyggen skal telle det, ikke skjule det.
     */
    @Test
    fun `en status som ikke lenger gir søknadsrett forklares med statusaksen`() {
        val utfall = sammenlign(
            gammel = listOf(gammelRad()),
            ny = listOf(testdeltakelse(kildestatus = Arenastatus.Kjent(Arenastatus.Type.IKKE_MOTT))),
        )

        utfall.kunIGammel shouldContainExactly listOf(KunIGammel(id = "TA1234567", grunn = Fraværsgrunn.STATUS_UTEN_RETT))
    }

    /**
     * En deltakelse uten sluttdato er like gyldig som en med, og klassifiseres på statusen sin på samme måte.
     * Kilden lar ofte sluttdatoen stå tom, særlig mens deltakelsen pågår.
     */
    @Test
    fun `en deltakelse uten sluttdato klassifiseres på samme måte`() {
        val utfall = sammenlign(
            gammel = listOf(gammelRad(deltakelseTom = null)),
            ny = listOf(testdeltakelse(kildestatus = Arenastatus.Kjent(Arenastatus.Type.IKKE_MOTT), tilOgMed = null)),
        )

        utfall.kunIGammel shouldContainExactly listOf(KunIGammel(id = "TA1234567", grunn = Fraværsgrunn.STATUS_UTEN_RETT))
    }

    @Test
    fun `en ukjent kildestatus skilles fra en status uten rett`() {
        val utfall = sammenlign(
            gammel = listOf(gammelRad()),
            ny = listOf(testdeltakelse(kildestatus = Arenastatus.Ukjent("EN_NY_STATUS"))),
        )

        utfall.kunIGammel shouldContainExactly listOf(KunIGammel(id = "TA1234567", grunn = Fraværsgrunn.UKJENT_KILDESTATUS))
    }

    @Test
    fun `en tiltakskode uten rett og en vi ikke kjenner får hver sin grunn`() {
        val utenRett = sammenlign(
            gammel = listOf(gammelRad()),
            ny = listOf(testdeltakelse(tiltakstype = Tiltakstype.SomIkkeGirRett("AMBF1"))),
        )
        utenRett.kunIGammel.single().grunn shouldBe Fraværsgrunn.TILTAKSTYPE_UTEN_RETT

        val ukjent = sammenlign(
            gammel = listOf(gammelRad()),
            ny = listOf(testdeltakelse(tiltakstype = Tiltakstype.Ukjent("EN_NY_KODE"))),
        )
        ukjent.kunIGammel.single().grunn shouldBe Fraværsgrunn.UKJENT_TILTAKSKODE
    }

    @Test
    fun `datoer som ikke henger sammen forklares som ugyldige`() {
        val utfall = sammenlign(
            gammel = listOf(gammelRad()),
            ny = listOf(testdeltakelse(fraOgMed = LocalDate.of(2026, 4, 1), tilOgMed = LocalDate.of(2026, 3, 1))),
        )

        utfall.kunIGammel shouldContainExactly listOf(KunIGammel(id = "TA1234567", grunn = Fraværsgrunn.UGYLDIGE_DATOER))
    }

    /**
     * Begge veier siles gjennom det samme tidsromsfilteret, så en rad kan bare falle ut på den ene siden når de to har ulike datoer.
     * Da er datoforskjellen funnet, og bøtta sier nettopp det.
     */
    @Test
    fun `en rad som passerer søknadsguarden men faller utenfor tidsrommet skilles ut`() {
        val utfall = sammenlign(
            gammel = listOf(gammelRad(deltakelseFom = testStart, deltakelseTom = testStart.plusDays(1))),
            ny = listOf(testdeltakelse(fraOgMed = LocalDate.of(2020, 1, 1), tilOgMed = LocalDate.of(2020, 6, 1))),
        )

        utfall.kunIGammel shouldContainExactly listOf(KunIGammel(id = "TA1234567", grunn = Fraværsgrunn.UTENFOR_TIDSROM))
    }

    @Test
    fun `en rad utenfor tidsrommet på begge sider sammenlignes ikke i det hele tatt`() {
        val gammelDato = LocalDate.of(2020, 1, 1)
        val utfall = sammenlign(
            gammel = listOf(gammelRad(deltakelseFom = gammelDato, deltakelseTom = gammelDato.plusMonths(1))),
            ny = listOf(testdeltakelse(fraOgMed = gammelDato, tilOgMed = gammelDato.plusMonths(1))),
        )

        utfall.harAvvik shouldBe false
        utfall.antallFelles shouldBe 0
    }

    /**
     * Ukjente kildeverdier og manglende kilder er ikke uenighet mellom veiene — gammel vei kunne aldri fortalt oss om dem.
     * De rapporteres, men gjør ikke kjøringen til en avvikskjøring.
     */
    @Test
    fun `funn ny vei bærer alene rapporteres uten å telle som avvik`() {
        val utfall = sammenlignSkygge(
            gammel = listOf(gammelRad()),
            ny = Tiltakshistorikk(
                deltakelser = Tiltaksdeltakelser(listOf(testdeltakelse())),
                meldinger = Tiltakshistorikkmeldinger(
                    listOf(Tiltakshistorikkmelding.ManglerHistorikkFraTeamTiltak, Tiltakshistorikkmelding.Ukjent("EN_NY_MELDING")),
                ),
                ukjenteDeltakelsesformer = UkjenteDeltakelsesformer(listOf(UkjentDeltakelsesform("EnNyDeltakelsesform"))),
                hentetTidspunkt = LocalDateTime.of(2026, 4, 1, 12, 0),
            ),
            iDato = iDato,
        )

        utfall.harAvvik shouldBe false
        utfall.manglendeKilder shouldBe setOf(Tiltakskilde.TeamTiltak)
        utfall.ukjenteKildeverdier.map { it.kodeIKontrakten } shouldContainExactlyInAnyOrder listOf("EN_NY_MELDING", "EnNyDeltakelsesform")
    }

    @Test
    fun `to veier med hver sin rad gir én i hver bøtte`() {
        val utfall = sammenlign(
            gammel = listOf(gammelRad(id = "TA1111111")),
            ny = listOf(testdeltakelse(id = "TA2222222")),
        )

        utfall.kunIGammel.single().id shouldBe "TA1111111"
        utfall.kunINy.single().id shouldBe "TA2222222"
        utfall.antallFelles shouldBe 0
    }

    private fun sammenlign(gammel: List<TiltakshistorikkDTO>, ny: List<Tiltaksdeltakelse>) = sammenlignSkygge(
        gammel = gammel,
        ny = Tiltakshistorikk(
            deltakelser = Tiltaksdeltakelser(ny),
            meldinger = Tiltakshistorikkmeldinger(emptyList()),
            ukjenteDeltakelsesformer = UkjenteDeltakelsesformer(emptyList()),
            hentetTidspunkt = LocalDateTime.of(2026, 4, 1, 12, 0),
        ),
        iDato = iDato,
    )

    /**
     * Raden slik dagens kjede leverer den, med verdier som matcher `testdeltakelse` felt for felt.
     * Gjennomføringen står tom, som den gjør for Arena og Team Tiltak i dag.
     */
    private fun gammelRad(
        id: String = "TA1234567",
        gjennomforingId: String = "",
        arenaKode: TiltakResponsDTO.TiltakTypeDTO = TiltakResponsDTO.TiltakTypeDTO.INDOPPFAG,
        typeNavn: String = "Oppfølging",
        visningsnavn: String = "Oppfølging hos Arrangør AS",
        arrangornavn: String? = "Arrangør AS",
        deltakelseFom: LocalDate? = testStart,
        deltakelseTom: LocalDate? = testSlutt,
        antallDagerPerUke: Float? = 3f,
        deltakelseProsent: Float? = 60f,
    ) = TiltakshistorikkDTO(
        id = id,
        gjennomforing = TiltakshistorikkDTO.GjennomforingDTO(
            id = gjennomforingId,
            visningsnavn = visningsnavn,
            arrangornavn = arrangornavn,
            typeNavn = typeNavn,
            arenaKode = arenaKode,
            deltidsprosent = null,
        ),
        deltakelseFom = deltakelseFom,
        deltakelseTom = deltakelseTom,
        deltakelseStatus = TiltakResponsDTO.DeltakerStatusDTO.DELTAR,
        antallDagerPerUke = antallDagerPerUke,
        deltakelseProsent = deltakelseProsent,
        kilde = TiltakshistorikkDTO.Kilde.ARENA,
    )
}
