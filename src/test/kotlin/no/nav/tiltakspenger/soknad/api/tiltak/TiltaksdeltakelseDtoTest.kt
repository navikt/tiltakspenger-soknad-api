package no.nav.tiltakspenger.soknad.api.tiltak

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO.TiltakTypeDTO
import no.nav.tiltakspenger.libs.tiltak.toTiltakstypeSomGirRett
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.TiltakstypeSomGirRett
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TiltaksdeltakelseDtoTest {
    private val idag = LocalDate.now(fixedClock)

    private fun deltakelse(fra: LocalDate?, til: LocalDate?) = TiltaksdeltakelseDto(
        aktivitetId = "id",
        type = TiltakTypeDTO.ARBTREN,
        typeNavn = "Arbeidstrening",
        arenaRegistrertPeriode = Deltakelsesperiode(fra = fra, til = til),
        arrangør = "arrangør",
        gjennomforingId = "gjennomforingId",
        visningsnavn = null,
    )

    @Test
    fun `deltakelse uten fradato er alltid innenfor relevant tidsrom`() {
        deltakelse(fra = null, til = null).erInnenforRelevantTidsrom(fixedClock) shouldBe true
    }

    @Test
    fun `deltakelse med kun fradato er innenfor når fradatoen er mellom 6 mnd tilbake og 2 mnd frem`() {
        deltakelse(fra = idag, til = null).erInnenforRelevantTidsrom(fixedClock) shouldBe true
        deltakelse(fra = idag.minusMonths(7), til = null).erInnenforRelevantTidsrom(fixedClock) shouldBe false
    }

    @Test
    fun `deltakelse med full periode er innenfor når perioden overlapper det relevante tidsrommet`() {
        deltakelse(fra = idag.minusMonths(1), til = idag.plusMonths(1)).erInnenforRelevantTidsrom(fixedClock) shouldBe true
        deltakelse(fra = idag.minusMonths(9), til = idag.minusMonths(7)).erInnenforRelevantTidsrom(fixedClock) shouldBe false
    }

    /**
     * Verdiene pinnes i klartekst, ikke bare som rundtur.
     * `type` går videre inn i den innsendte søknaden og leses av saksbehandling-api med `TiltakTypeDTO.valueOf`, så en endret verdi her feller innsendingen der — ikke hos oss.
     */
    @Test
    fun `tiltakstype mappes til den arenakoden wiren har hatt hele tiden`() {
        TiltakstypeSomGirRett.entries.associateWith { it.tilArenakode() } shouldBe mapOf(
            TiltakstypeSomGirRett.ARBEIDSFORBEREDENDE_TRENING to TiltakTypeDTO.ARBFORB,
            TiltakstypeSomGirRett.ARBEIDSMARKEDSOPPLAERING to TiltakTypeDTO.ARBEIDSMARKEDSOPPLAERING,
            TiltakstypeSomGirRett.ARBEIDSRETTET_REHABILITERING to TiltakTypeDTO.ARBRRHDAG,
            TiltakstypeSomGirRett.ARBEIDSTRENING to TiltakTypeDTO.ARBTREN,
            TiltakstypeSomGirRett.AVKLARING to TiltakTypeDTO.AVKLARAG,
            TiltakstypeSomGirRett.DIGITAL_JOBBKLUBB to TiltakTypeDTO.DIGIOPPARB,
            TiltakstypeSomGirRett.ENKELTPLASS_AMO to TiltakTypeDTO.ENKELAMO,
            TiltakstypeSomGirRett.ENKELTPLASS_VGS_OG_HØYERE_YRKESFAG to TiltakTypeDTO.ENKFAGYRKE,
            TiltakstypeSomGirRett.FAG_OG_YRKESOPPLAERING to TiltakTypeDTO.FAG_OG_YRKESOPPLAERING,
            TiltakstypeSomGirRett.FORSØK_OPPLÆRING_LENGRE_VARIGHET to TiltakTypeDTO.FORSOPPLEV,
            TiltakstypeSomGirRett.GRUPPE_AMO to TiltakTypeDTO.GRUPPEAMO,
            TiltakstypeSomGirRett.GRUPPE_VGS_OG_HØYERE_YRKESFAG to TiltakTypeDTO.GRUFAGYRKE,
            TiltakstypeSomGirRett.HØYERE_UTDANNING to TiltakTypeDTO.HOYEREUTD,
            TiltakstypeSomGirRett.HOYERE_YRKESFAGLIG_UTDANNING to TiltakTypeDTO.HOYERE_YRKESFAGLIG_UTDANNING,
            TiltakstypeSomGirRett.INDIVIDUELL_JOBBSTØTTE to TiltakTypeDTO.INDJOBSTOT,
            TiltakstypeSomGirRett.INDIVIDUELL_KARRIERESTØTTE_UNG to TiltakTypeDTO.IPSUNG,
            TiltakstypeSomGirRett.JOBBKLUBB to TiltakTypeDTO.JOBBK,
            TiltakstypeSomGirRett.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV to TiltakTypeDTO.NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV,
            TiltakstypeSomGirRett.OPPFØLGING to TiltakTypeDTO.INDOPPFAG,
            TiltakstypeSomGirRett.STUDIESPESIALISERING to TiltakTypeDTO.STUDIESPESIALISERING,
            TiltakstypeSomGirRett.UTVIDET_OPPFØLGING_I_NAV to TiltakTypeDTO.UTVAOONAV,
            TiltakstypeSomGirRett.UTVIDET_OPPFØLGING_I_OPPLÆRING to TiltakTypeDTO.UTVOPPFOPL,
        )
    }

    /**
     * Mappingen over er en håndskrevet invers av `TiltakTypeDTO.toTiltakstypeSomGirRett()` i libs, som er kilden gammel vei brukte.
     * Testen fanger at de to kommer i utakt — for eksempel om libs flytter en arenakode til en annen tiltakstype.
     */
    @Test
    fun `arenakoden er den eksakte inversen av mappingen i libs`() {
        val fraLibs = TiltakTypeDTO.entries
            .mapNotNull { arenakode -> arenakode.toTiltakstypeSomGirRett().getOrNull()?.let { it.name to arenakode } }
            .toMap()

        TiltakstypeSomGirRett.entries.associate { it.name to it.tilArenakode() } shouldBe fraLibs
    }
}
