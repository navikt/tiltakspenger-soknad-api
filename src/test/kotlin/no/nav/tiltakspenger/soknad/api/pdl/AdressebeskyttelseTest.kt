package no.nav.tiltakspenger.soknad.api.pdl

import io.kotest.assertions.throwables.shouldThrow
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.Endring
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.EndringsMetadata
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.FolkeregisterMetadata
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.Kilde
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

const val FREG = "FREG"

internal class AdressebeskyttelseTest {

    fun mockFolkeregistermetadata(
        ajourholdstidspunkt: LocalDateTime = LocalDateTime.now(),
    ): FolkeregisterMetadata =
        FolkeregisterMetadata(
            aarsak = "test",
            ajourholdstidspunkt = ajourholdstidspunkt,
            gyldighetstidspunkt = LocalDateTime.now(),
            kilde = "test",
            opphoerstidspunkt = LocalDateTime.now(),
            sekvens = 0,
        )

    fun mockEndringsmetadata(
        master: String = FREG,
        registrert: LocalDateTime = LocalDateTime.now(),
        kilde: String = Kilde.BRUKER_SELV,
    ): EndringsMetadata =
        EndringsMetadata(
            master = master,
            endringer = listOf(
                Endring(
                    kilde = kilde,
                    registrert = registrert,
                    registrertAv = "test",
                    systemkilde = "test",
                    type = "test",
                ),
            ),
        )

    fun mockUdokumentertEndringsmetadata(endringstidspunkt: LocalDateTime = LocalDateTime.now()) =
        mockEndringsmetadata(master = Kilde.PDL, kilde = Kilde.BRUKER_SELV, registrert = endringstidspunkt)

    fun mockDokumentertEndringsmetadata(
        master: String = Kilde.PDL,
        kilde: String = Kilde.PDL,
        endringstidspunkt: LocalDateTime = LocalDateTime.now(),
    ) = mockEndringsmetadata(master = master, kilde = kilde, registrert = endringstidspunkt)

    fun mockAdressebeskyttelse(
        gradering: AdressebeskyttelseGradering = AdressebeskyttelseGradering.UGRADERT,
        folkeregistermetadata: FolkeregisterMetadata? = mockFolkeregistermetadata(),
        metadata: EndringsMetadata = mockEndringsmetadata(),
    ): Adressebeskyttelse =
        Adressebeskyttelse(
            gradering = gradering,
            folkeregistermetadata = folkeregistermetadata,
            metadata = metadata,
        )

    @Test
    fun `hvis liste med Adressebeskyttelse er tom, så er personen UGRADERT`() {
        val gradering = avklarGradering(gradering = emptyList())
        assertEquals(AdressebeskyttelseGradering.UGRADERT, gradering)
    }

    @Test
    fun `hvis liste med Adressebeskyttelse har ett element, så plukker vi graderingen som er spesifisert, sålenge den er dokumentert`() {
        val adressebeskyttelse = mockAdressebeskyttelse(
            gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND,
            metadata = mockDokumentertEndringsmetadata(),
        )
        val gradering = avklarGradering(gradering = listOf(adressebeskyttelse))
        assertEquals(AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND, gradering)
    }

    @Test
    fun `hvis liste med Adressebeskyttelse har ett element som er udokumentert, så kaster vi en feil`() {
        val adressebeskyttelse = mockAdressebeskyttelse(
            gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND,
            metadata = mockUdokumentertEndringsmetadata(),
        )
        shouldThrow<IllegalStateException> {
            avklarGradering(gradering = listOf(adressebeskyttelse))
        }
    }

    @Test
    fun `hvis liste med Adressebeskyttelse har flere elementer, så plukker vi graderingen med seneste endringstidspunkt`() {
        val strengtFortroligUtlandAdressebeskyttelse = mockAdressebeskyttelse(
            gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND,
            metadata = mockDokumentertEndringsmetadata(),
        )
        val strengtFortroligAdressebeskyttelse = mockAdressebeskyttelse(
            gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG,
            metadata = mockDokumentertEndringsmetadata(
                master = FREG,
                kilde = FREG,
            ),
            folkeregistermetadata = mockFolkeregistermetadata(
                ajourholdstidspunkt = LocalDateTime.MAX,
            ),
        )
        val fortroligAdressebeskyttelse = mockAdressebeskyttelse(
            gradering = AdressebeskyttelseGradering.FORTROLIG,
            metadata = mockDokumentertEndringsmetadata(
                master = FREG,
                kilde = FREG,
            ),
        )

        val gradering = avklarGradering(
            gradering = listOf(
                fortroligAdressebeskyttelse,
                strengtFortroligAdressebeskyttelse,
                strengtFortroligUtlandAdressebeskyttelse,
            ),
        )
        assertEquals(AdressebeskyttelseGradering.STRENGT_FORTROLIG, gradering)
    }

    @Test
    fun `hvis liste med Adressebeskyttelse har flere elementer, og inneholder UGRADERT og STRENGT_FORTROLIG_UTLAND, skal vi alltid plukke STRENGT_FORTROLIG_UTLAND`() {
        val strengtFortroligUtlandAdressebeskyttelse = mockAdressebeskyttelse(
            gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND,
            metadata = mockDokumentertEndringsmetadata(
                endringstidspunkt = LocalDateTime.MIN,
            ),
        )

        val ugradertAdressebeskyttelse = mockAdressebeskyttelse(
            gradering = AdressebeskyttelseGradering.UGRADERT,
            metadata = mockDokumentertEndringsmetadata(
                master = FREG,
                kilde = FREG,
                endringstidspunkt = LocalDateTime.MAX,
            ),
        )

        val gradering = avklarGradering(
            gradering = listOf(
                strengtFortroligUtlandAdressebeskyttelse,
                ugradertAdressebeskyttelse,
            ),
        )

        assertEquals(AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND, gradering)
    }

    @Test
    fun `hvis liste med Adressebeskyttelse kun inneholder udokumenterte endringer, kastes det en feil`() {
        val udokumentertAdressebeskyttelse1 = mockAdressebeskyttelse(
            gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND,
            metadata = mockUdokumentertEndringsmetadata(),
        )
        val udokumentertAdressebeskyttelse2 = mockAdressebeskyttelse(
            gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG,
            metadata = mockUdokumentertEndringsmetadata(),
        )
        shouldThrow<IllegalStateException> {
            avklarGradering(
                gradering = listOf(
                    udokumentertAdressebeskyttelse1,
                    udokumentertAdressebeskyttelse2,
                ),
            )
        }
    }

    @Test
    fun `hvis liste med Adressebeskyttelse kun inneholder udokumenterte endringer og ugradert, kastes det en feil`() {
        val udokumentertAdressebeskyttelse1 = mockAdressebeskyttelse(
            gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND,
            metadata = mockUdokumentertEndringsmetadata(),
        )
        val udokumentertAdressebeskyttelse2 = mockAdressebeskyttelse(
            gradering = AdressebeskyttelseGradering.UGRADERT,
            metadata = mockDokumentertEndringsmetadata(),
        )
        shouldThrow<IllegalStateException> {
            avklarGradering(
                gradering = listOf(
                    udokumentertAdressebeskyttelse1,
                    udokumentertAdressebeskyttelse2,
                ),
            )
        }
    }
}
