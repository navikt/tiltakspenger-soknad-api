package no.nav.tiltakspenger.soknad.api.saksbehandlingApi

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.json.objectMapper
import no.nav.tiltakspenger.libs.soknad.OppholdsstedDTO
import no.nav.tiltakspenger.libs.soknad.SpmSvarDTO
import no.nav.tiltakspenger.soknad.api.mockBarnetillegg
import no.nav.tiltakspenger.soknad.api.mockManueltRegistrertBarn
import no.nav.tiltakspenger.soknad.api.mockRegistrertBarn
import no.nav.tiltakspenger.soknad.api.soknad.Oppholdssted
import no.nav.tiltakspenger.soknad.api.soknad.validering.spørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.soknad.validering.søknad
import org.junit.jupiter.api.Test

class SøknadMapperTest {
    @Test
    fun `mapper og serialiserer journalpostId som streng`() {
        val dto = søknadMapper(
            søknad = søknad(),
            jounalpostId = JournalpostId("123456789"),
            saksnummer = "SAK-1",
        )

        val json = objectMapper.writeValueAsString(dto)

        dto.journalpostId shouldBe "123456789"
        json shouldContain "\"journalpostId\":\"123456789\""
    }

    @Test
    fun `nullstiller andre utbetalinger og mapper Nei-svar når bruker ikke mottar andre utbetalinger`() {
        val dto = søknadMapper(
            søknad = søknad(spørsmålsbesvarelser = spørsmålsbesvarelser(mottarAndreUtbetalinger = false)),
            jounalpostId = JournalpostId("123456789"),
            saksnummer = "SAK-1",
        )

        dto.gjenlevendepensjon.svar shouldBe SpmSvarDTO.Nei
        dto.gjenlevendepensjon.fom shouldBe null
        dto.gjenlevendepensjon.tom shouldBe null
        dto.alderspensjon.svar shouldBe SpmSvarDTO.Nei
        dto.alderspensjon.fom shouldBe null
        dto.supplerendeStønadFlyktning.svar shouldBe SpmSvarDTO.Nei
        dto.supplerendeStønadAlder.svar shouldBe SpmSvarDTO.Nei
        dto.jobbsjansen.svar shouldBe SpmSvarDTO.Nei
        dto.trygdOgPensjon.svar shouldBe SpmSvarDTO.Nei
    }

    @Test
    fun `mapper oppholdssted for barn, og lar det være null for barn som bare har svart ja eller nei`() {
        val dto = søknadMapper(
            søknad = søknad(
                spørsmålsbesvarelser = spørsmålsbesvarelser(
                    barnetillegg = mockBarnetillegg(
                        registrerteBarnSøktBarnetilleggFor = listOf(
                            mockRegistrertBarn(oppholdInnenforEøs = null, oppholdssted = Oppholdssted.NORGE),
                            mockRegistrertBarn(oppholdInnenforEøs = null, oppholdssted = Oppholdssted.ANNET_EØS_LAND),
                            mockRegistrertBarn(oppholdInnenforEøs = false, oppholdssted = null),
                        ),
                        manueltRegistrerteBarnSøktBarnetilleggFor = listOf(
                            mockManueltRegistrertBarn(oppholdInnenforEøs = null, oppholdssted = Oppholdssted.IKKE_EØS_LAND),
                        ),
                    ),
                ),
            ),
            jounalpostId = JournalpostId("123456789"),
            saksnummer = "SAK-1",
        )

        dto.barnetilleggPdl.map { it.oppholdssted } shouldBe listOf(OppholdsstedDTO.NORGE, OppholdsstedDTO.ANNET_EØS_LAND, null)
        dto.barnetilleggPdl.map { it.oppholderSegIEØS.svar } shouldBe listOf(SpmSvarDTO.Ja, SpmSvarDTO.Ja, SpmSvarDTO.Nei)
        dto.barnetilleggManuelle.single().oppholdssted shouldBe OppholdsstedDTO.IKKE_EØS_LAND
        dto.barnetilleggManuelle.single().oppholderSegIEØS.svar shouldBe SpmSvarDTO.Nei
    }

    @Test
    fun `mapper svaret på om søkeren vil søke barnetillegg, og lar det være null når det mangler`() {
        fun harSøktOmBarnetillegg(søkerOmBarnetillegg: Boolean?) = søknadMapper(
            søknad = søknad(
                spørsmålsbesvarelser = spørsmålsbesvarelser(
                    barnetillegg = mockBarnetillegg(søkerOmBarnetillegg = søkerOmBarnetillegg),
                ),
            ),
            jounalpostId = JournalpostId("123456789"),
            saksnummer = "SAK-1",
        ).harSøktOmBarnetillegg?.svar

        harSøktOmBarnetillegg(true) shouldBe SpmSvarDTO.Ja
        harSøktOmBarnetillegg(false) shouldBe SpmSvarDTO.Nei
        harSøktOmBarnetillegg(null) shouldBe null
    }
}
