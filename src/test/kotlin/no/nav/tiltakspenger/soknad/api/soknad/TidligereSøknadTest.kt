package no.nav.tiltakspenger.soknad.api.soknad

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.libs.common.random
import no.nav.tiltakspenger.soknad.api.soknad.validering.barnetillegg
import no.nav.tiltakspenger.soknad.api.soknad.validering.spørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.soknad.validering.tiltak
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TidligereSøknadTest {
    private val januar = Periode(fra = LocalDate.of(2025, 1, 1), til = LocalDate.of(2025, 1, 31))
    private val svar = spørsmålsbesvarelser(tiltak = tiltak(aktivitetId = "aktivitet", periode = januar))

    private fun tidligere(
        svar: SpørsmålsbesvarelserDTO = this.svar,
        mottatt: LocalDateTime = LocalDateTime.of(2025, 1, 2, 10, 0),
        behandlingsutfall: Behandlingsutfall? = null,
    ) = TidligereSøknad(id = SøknadId.random(), mottatt = mottatt, svar = svar, behandlingsutfall = behandlingsutfall)

    @Nested
    inner class SøknadSomDekker {
        @Test
        fun `samme deltakelse og periode er dekket`() {
            val søknad = tidligere()

            listOf(søknad).søknadSomDekker("aktivitet", januar) shouldBe søknad
        }

        @Test
        fun `en periode innenfor den søkte er dekket`() {
            val søknad = tidligere()

            listOf(søknad).søknadSomDekker("aktivitet", Periode(LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 20))) shouldBe søknad
        }

        @Test
        fun `en forlenget periode er ikke dekket`() {
            listOf(tidligere()).søknadSomDekker("aktivitet", Periode(januar.fra, LocalDate.of(2025, 2, 28))) shouldBe null
        }

        @Test
        fun `en annen deltakelse er ikke dekket`() {
            listOf(tidligere()).søknadSomDekker("annen aktivitet", januar) shouldBe null
        }

        @Test
        fun `velger den sist mottatte når flere dekker`() {
            val første = tidligere(mottatt = LocalDateTime.of(2025, 1, 2, 10, 0))
            val siste = tidligere(mottatt = LocalDateTime.of(2025, 1, 5, 10, 0))

            listOf(siste, første).søknadSomDekker("aktivitet", januar) shouldBe siste
        }
    }

    @Nested
    inner class DuplikatAv {
        @Test
        fun `samme deltakelse, periode og svar er en duplikat`() {
            val søknad = tidligere()

            listOf(søknad).duplikatAv(svar) shouldBe søknad
        }

        @Test
        fun `en innvilget, avslått eller avbrutt søknad med de samme svarene er ikke en duplikat`() {
            listOf(Behandlingsresultat.INNVILGELSE, Behandlingsresultat.AVSLAG, Behandlingsresultat.AVBRUTT).forEach { resultat ->
                val ferdigBehandlet = tidligere(behandlingsutfall = behandlingsutfall(resultat))

                listOf(ferdigBehandlet).duplikatAv(svar) shouldBe null
            }
        }

        @Test
        fun `en gjenåpnet søknad med de samme svarene er en duplikat, fordi den er under behandling igjen`() {
            val gjenåpnet = tidligere(behandlingsutfall = behandlingsutfall(Behandlingsresultat.GJENÅPNET))

            listOf(gjenåpnet).duplikatAv(svar) shouldBe gjenåpnet
        }

        private fun behandlingsutfall(resultat: Behandlingsresultat) = Behandlingsutfall(
            resultat = resultat,
            tidspunkt = LocalDateTime.of(2025, 1, 10, 10, 0),
            vedtakId = if (resultat.harVedtak) "vedtak" else null,
        )

        @Test
        fun `et endret svar er ikke en duplikat`() {
            listOf(tidligere()).duplikatAv(svar.copy(etterlønn = Etterlønn(mottar = false))) shouldBe null
        }

        @Test
        fun `en annen deltakelse eller en forlenget periode er ikke en duplikat`() {
            val tidligere = listOf(tidligere())

            tidligere.duplikatAv(svar.copy(tiltak = tiltak(aktivitetId = "annen", periode = januar))) shouldBe null
            tidligere.duplikatAv(svar.copy(tiltak = tiltak(aktivitetId = "aktivitet", periode = januar.copy(til = LocalDate.of(2025, 2, 28))))) shouldBe null
        }

        @Test
        fun `sammenligner bare med den sist mottatte søknaden`() {
            val endret = svar.copy(etterlønn = Etterlønn(mottar = false))
            val første = tidligere(svar = svar, mottatt = LocalDateTime.of(2025, 1, 2, 10, 0))
            val siste = tidligere(svar = endret, mottatt = LocalDateTime.of(2025, 1, 5, 10, 0))

            listOf(første, siste).duplikatAv(svar) shouldBe null
            listOf(første, siste).duplikatAv(endret) shouldBe siste
        }

        @Test
        fun `tiltakets beskrivelse fra kilden og bekreftelsene teller ikke som endringer`() {
            val nyeSvar = svar.copy(
                tiltak = Tiltak(
                    aktivitetId = "aktivitet",
                    periode = januar,
                    arenaRegistrertPeriode = null,
                    arrangør = "Nytt navn på arrangør",
                    type = "ARBTREN",
                    typeNavn = "Nytt typenavn",
                    gjennomforingId = "ny",
                    visningsnavn = "Nytt visningsnavn",
                ),
                harBekreftetAlleOpplysninger = !svar.harBekreftetAlleOpplysninger,
            )

            val søknad = tidligere()

            listOf(søknad).duplikatAv(nyeSvar) shouldBe søknad
        }

        @Test
        fun `en periode som hører til et nei-svar teller ikke`() {
            val nei = svar.copy(sykepenger = Sykepenger(mottar = false, periode = null))
            val neiMedGammelPeriode = svar.copy(sykepenger = Sykepenger(mottar = false, periode = januar))
            val søknad = tidligere(svar = nei)

            listOf(søknad).duplikatAv(neiMedGammelPeriode) shouldBe søknad
        }

        @Test
        fun `svar under andre utbetalinger teller ikke når bruker svarte nei på andre utbetalinger`() {
            val nei = svar.copy(mottarAndreUtbetalinger = false)
            val søknad = tidligere(svar = nei)

            listOf(søknad).duplikatAv(nei.copy(jobbsjansen = Jobbsjansen(mottar = false, periode = null))) shouldBe søknad
            listOf(søknad).duplikatAv(nei.copy(sykepenger = Sykepenger(mottar = false, periode = null))) shouldBe null
        }

        @Test
        fun `rekkefølgen på barna teller ikke, men et nytt barn gjør det`() {
            val barn1 = ManueltRegistrertBarn("Kari", null, "Nordmann", LocalDate.of(2020, 1, 1), null, Oppholdssted.NORGE)
            val barn2 = ManueltRegistrertBarn("Ola", null, "Nordmann", LocalDate.of(2021, 1, 1), null, Oppholdssted.NORGE)
            fun medBarn(vararg barn: ManueltRegistrertBarn) = svar.copy(
                barnetillegg = barnetillegg(
                    manueltRegistrerteBarnSøktBarnetilleggFor = barn.toList(),
                    registrerteBarnSøktBarnetilleggFor = emptyList(),
                    søkerOmBarnetillegg = true,
                ),
            )
            val søknad = tidligere(svar = medBarn(barn1, barn2))

            listOf(søknad).duplikatAv(medBarn(barn2, barn1)) shouldBe søknad
            listOf(søknad).duplikatAv(medBarn(barn1)) shouldBe null
        }
    }
}
