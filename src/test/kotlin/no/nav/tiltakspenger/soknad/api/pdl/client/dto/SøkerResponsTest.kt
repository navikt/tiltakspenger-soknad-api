package no.nav.tiltakspenger.soknad.api.pdl.client.dto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.personklient.pdl.dto.PdlPersonBolkCode
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Kanttilfeller i mappingen fra PDL-svaret til domenet, som ikke lar seg framprovosere gjennom rutene.
 * Selve happy-pathen dekkes ende-til-ende av rute- og jobb-testene.
 */
internal class SøkerResponsTest {
    private val fødselsnummer = "02058938710"
    private val barnFødselsnummer = "21062002856"

    private fun metadata(master: String = "FREG", endringer: List<Endring> = emptyList()) =
        EndringsMetadata(endringer = endringer, master = master)

    private fun folkeregistermetadata() = FolkeregisterMetadata(
        aarsak = null,
        ajourholdstidspunkt = null,
        gyldighetstidspunkt = null,
        kilde = null,
        opphoerstidspunkt = null,
        sekvens = null,
    )

    private fun navn(metadata: EndringsMetadata = metadata()) = Navn(
        fornavn = "foo",
        mellomnavn = "baz",
        etternavn = "bar",
        metadata = metadata,
        folkeregistermetadata = folkeregistermetadata(),
    )

    private fun søkerRespons(doedsfall: List<Dødsfall> = emptyList()) = SøkerRespons(
        hentPerson = SøkerFraPDL(
            navn = listOf(navn()),
            adressebeskyttelse = emptyList(),
            forelderBarnRelasjon = emptyList(),
            doedsfall = doedsfall,
            foedselsdato = listOf(
                Fødsel(
                    foedselsdato = LocalDate.of(1989, 5, 2),
                    metadata = metadata(),
                    folkeregistermetadata = folkeregistermetadata(),
                ),
            ),
        ),
        hentGeografiskTilknytning = null,
    )

    @Test
    fun `toPerson kaster når søker er registrert som død i PDL`() {
        val respons = søkerRespons(doedsfall = listOf(Dødsfall(doedsdato = LocalDate.of(2020, 1, 1))))

        shouldThrow<IllegalStateException> { respons.toPerson(Fnr.fromString(fødselsnummer)) }
    }

    @Test
    fun `toPerson kaster når personen ikke ble funnet`() {
        val respons = SøkerRespons(hentPerson = null, hentGeografiskTilknytning = null)

        shouldThrow<IllegalStateException> { respons.toPerson(Fnr.fromString(fødselsnummer)) }
    }

    @Test
    fun `avklarNavn kaster når alle navnene har udokumentert kilde`() {
        val udokumentertNavn = navn(
            metadata = metadata(
                master = Kilde.PDL,
                endringer = listOf(
                    Endring(
                        kilde = Kilde.BRUKER_SELV,
                        registrert = LocalDateTime.of(2020, 1, 1, 12, 0),
                        registrertAv = "bruker",
                        systemkilde = "test",
                        type = "OPPRETT",
                    ),
                ),
            ),
        )

        shouldThrow<IllegalStateException> { avklarNavn(listOf(udokumentertNavn)) }
    }

    @Test
    fun `avklarNavn kaster når det ikke finnes noe navn`() {
        shouldThrow<IllegalStateException> { avklarNavn(emptyList()) }
    }

    @Test
    fun `avklarFødsel kaster når det ikke finnes noen fødselsdato`() {
        shouldThrow<IllegalStateException> { avklarFødsel(emptyList()) }
    }

    @Test
    fun `getGT returnerer riktig verdi for alle typene geografisk tilknytning`() {
        GeografiskTilknytning(gtType = "KOMMUNE", gtKommune = "1122", gtBydel = null, gtLand = null).getGT() shouldBe "1122"
        GeografiskTilknytning(gtType = "BYDEL", gtKommune = null, gtBydel = "112233", gtLand = null).getGT() shouldBe "112233"
        GeografiskTilknytning(gtType = "UTLAND", gtKommune = null, gtBydel = null, gtLand = "SWE").getGT() shouldBe "SWE"
        GeografiskTilknytning(gtType = "UDEFINERT", gtKommune = null, gtBydel = null, gtLand = null).getGT() shouldBe "UDEFINERT"
        GeografiskTilknytning(gtType = "UKJENT_TYPE", gtKommune = null, gtBydel = null, gtLand = null).getGT() shouldBe null
    }

    @Test
    fun `toPersoner gir null for barn som ikke ble funnet i bolkoppslaget`() {
        val respons = SøkersBarnRespons(
            hentPersonBolk = listOf(
                SøkersBarnFraPDLBolk(
                    ident = barnFødselsnummer,
                    person = null,
                    code = PdlPersonBolkCode.NOT_FOUND,
                ),
            ),
        )

        respons.toPersoner() shouldBe listOf(null)
    }
}
