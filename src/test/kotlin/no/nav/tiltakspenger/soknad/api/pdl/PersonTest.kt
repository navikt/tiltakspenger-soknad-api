package no.nav.tiltakspenger.soknad.api.pdl

import io.mockk.mockkClass
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.EndringsMetadata
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.FolkeregisterMetadata
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.ForelderBarnRelasjon
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.ForelderBarnRelasjonRolle
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class PersonTest {
    private fun mockForelderBarnRelasjon(id: String?, rolle: ForelderBarnRelasjonRolle): ForelderBarnRelasjon {
        return ForelderBarnRelasjon(
            relatertPersonsIdent = id,
            relatertPersonsRolle = rolle,
            metadata = mockkClass(EndringsMetadata::class),
            folkeregistermetadata = mockkClass(FolkeregisterMetadata::class),
        )
    }

    private fun mockPerson(
        fnr: Fnr = Fnr.fromString("02058938710"),
        gradering: AdressebeskyttelseGradering = AdressebeskyttelseGradering.UGRADERT,
        forelderBarnRelasjon: List<ForelderBarnRelasjon> = emptyList(),
        erDød: Boolean = false,
        fornavn: String = "foo",
        fødselsdato: LocalDate = LocalDate.MAX,
        geografiskTilknytning: String? = "1122",
    ): Person {
        return Person(
            fnr = fnr,
            fornavn = fornavn,
            mellomnavn = "baz",
            etternavn = "bar",
            adressebeskyttelseGradering = gradering,
            fødselsdato = fødselsdato,
            forelderBarnRelasjon = forelderBarnRelasjon,
            erDød = erDød,
            geografiskTilknytning = geografiskTilknytning,
        )
    }

    private val testpersonUgradert = mockPerson(gradering = AdressebeskyttelseGradering.UGRADERT)
    private val testpersonFortrolig = mockPerson(gradering = AdressebeskyttelseGradering.FORTROLIG)
    private val testpersonStrengtFortrolig = mockPerson(gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG)
    private val testpersonStrengtFortroligUtland =
        mockPerson(gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND)
    private val dødTestPerson = mockPerson(erDød = true, fornavn = "Død")

    private val testpersonMedRelasjoner = mockPerson(
        forelderBarnRelasjon = listOf(
            mockForelderBarnRelasjon(id = "1", rolle = ForelderBarnRelasjonRolle.BARN),
            mockForelderBarnRelasjon(id = "1", rolle = ForelderBarnRelasjonRolle.BARN),
            mockForelderBarnRelasjon(id = null, rolle = ForelderBarnRelasjonRolle.BARN),
            mockForelderBarnRelasjon(id = "2", rolle = ForelderBarnRelasjonRolle.BARN),
            mockForelderBarnRelasjon(id = "3", rolle = ForelderBarnRelasjonRolle.MOR),
            mockForelderBarnRelasjon(id = "4", rolle = ForelderBarnRelasjonRolle.FAR),
            mockForelderBarnRelasjon(id = "5", rolle = ForelderBarnRelasjonRolle.MEDMOR),
        ),
    )

    @Test
    fun `toPersonDTO skal returnere personens navn og en tom liste med barn når det ikke er noen barn`() {
        val personDTO = testpersonUgradert.toPersonDTO(emptyList())
        assertEquals(testpersonUgradert.fornavn, personDTO.fornavn)
        assertEquals(testpersonUgradert.mellomnavn, personDTO.mellomnavn)
        assertEquals(testpersonUgradert.etternavn, personDTO.etternavn)
        assertTrue(personDTO.barn.isEmpty())
    }

    @Test
    fun `toPersonDTO skal returnere personens navn og en liste med barn når man oppgir barn`() {
        val personDTO = testpersonUgradert.toPersonDTO(listOf(testpersonUgradert))
        assertEquals(testpersonUgradert.fornavn, personDTO.fornavn)
        assertEquals(testpersonUgradert.mellomnavn, personDTO.mellomnavn)
        assertEquals(testpersonUgradert.etternavn, personDTO.etternavn)
        assertTrue(personDTO.barn.size == 1)

        val ugradertBart = personDTO.barn[0]
        assertEquals(testpersonUgradert.fornavn, ugradertBart.fornavn)
        assertEquals(testpersonUgradert.mellomnavn, ugradertBart.mellomnavn)
        assertEquals(testpersonUgradert.etternavn, ugradertBart.etternavn)
    }

    @Test
    fun `toPersonDTO skal ikke returnere navn på barn som har AdressebeskyttelseGradering FORTROLIG`() {
        val personDTO = testpersonUgradert.toPersonDTO(listOf(testpersonFortrolig))
        val fortroligBarn = personDTO.barn[0]
        assertNull(fortroligBarn.fornavn)
        assertNull(fortroligBarn.mellomnavn)
        assertNull(fortroligBarn.etternavn)
    }

    @Test
    fun `toPersonDTO skal ikke returnere navn på barn som har AdressebeskyttelseGradering STRENGT_FORTROLIG`() {
        val personDTO = testpersonUgradert.toPersonDTO(listOf(testpersonStrengtFortrolig))
        val fortroligBarn = personDTO.barn[0]
        assertNull(fortroligBarn.fornavn)
        assertNull(fortroligBarn.mellomnavn)
        assertNull(fortroligBarn.etternavn)
    }

    @Test
    fun `toPersonDTO skal ikke returnere navn på barn som har AdressebeskyttelseGradering STRENGT_FORTROLIG_UTLAND`() {
        val personDTO = testpersonUgradert.toPersonDTO(listOf(testpersonStrengtFortroligUtland))
        val fortroligBarn = personDTO.barn[0]
        assertNull(fortroligBarn.fornavn)
        assertNull(fortroligBarn.mellomnavn)
        assertNull(fortroligBarn.etternavn)
    }

    @Test
    fun `barnsIdenter skal returnere relatertPersonsIdent fra barn forelderBarnRelasjon, og ikke inneholde null eller duplikate verdier`() {
        val barnsIdenter = testpersonMedRelasjoner.barnsIdenter()
        assertTrue(barnsIdenter.size == 2)
        assertTrue(barnsIdenter.distinct().size == 2)
        assertTrue(barnsIdenter.size == 2)
        assertEquals(barnsIdenter[0], "1")
        assertEquals(barnsIdenter[1], "2")
    }

    @Test
    fun `barn med dødsdato skal filtreres ut`() {
        val personDTO = testpersonUgradert.toPersonDTO(listOf(testpersonUgradert, dødTestPerson))
        assertEquals(personDTO.barn.size, 1)
        val barn = personDTO.barn[0]
        assertNotEquals(barn.fornavn, "Død")
    }

    @Test
    fun `toPersonDTO skal sette harFylt18År = false hvis fødselsdato på personen er mindre enn 18 år tilbake i tid`() {
        val fødselsdatoUnder18År = LocalDate.now().minusYears(18).plusDays(1)
        val personSomIkkeHarFylt18År = mockPerson(fødselsdato = fødselsdatoUnder18År)
        val harFylt18År = personSomIkkeHarFylt18År.toPersonDTO().harFylt18År
        assertFalse(harFylt18År!!)
    }

    @Test
    fun `toPersonDTO skal sette harFylt18År = true hvis fødselsdato på personen 18 år tilbake i tid`() {
        val fødselsdato18År = LocalDate.now().minusYears(18)
        val personSomHarFylt18År = mockPerson(fødselsdato = fødselsdato18År)
        val harFylt18År = personSomHarFylt18År.toPersonDTO().harFylt18År
        assertTrue(harFylt18År!!)
    }
}
