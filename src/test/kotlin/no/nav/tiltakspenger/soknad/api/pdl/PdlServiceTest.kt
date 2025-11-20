package no.nav.tiltakspenger.soknad.api.pdl

import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.personklient.pdl.dto.PdlPersonBolkCode
import no.nav.tiltakspenger.soknad.api.pdl.client.PdlClient
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.Dødsfall
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.EndringsMetadata
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.FolkeregisterMetadata
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.ForelderBarnRelasjon
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.ForelderBarnRelasjonRolle
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.Fødsel
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.GeografiskTilknytning
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.Navn
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkerFraPDL
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkerRespons
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkersBarnFraPDL
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkersBarnFraPDLBolk
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkersBarnRespons
import no.nav.tiltakspenger.soknad.api.pdl.routes.dto.BarnDTO
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class PdlServiceTest {
    val testFødselsnummer = "02058938710"
    val testBarnFødselsnummer = "21062002856"

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    fun mockEndringsMetadata(): EndringsMetadata {
        return EndringsMetadata(
            endringer = emptyList(),
            master = "test",
        )
    }

    fun mockFolkeregisterMetadata(): FolkeregisterMetadata {
        return FolkeregisterMetadata(
            aarsak = null,
            ajourholdstidspunkt = null,
            gyldighetstidspunkt = null,
            kilde = null,
            opphoerstidspunkt = null,
            sekvens = null,
        )
    }

    fun mockNavn(): Navn {
        return Navn(
            fornavn = "foo",
            mellomnavn = "baz",
            etternavn = "bar",
            metadata = mockEndringsMetadata(),
            folkeregistermetadata = mockFolkeregisterMetadata(),
        )
    }

    fun mockFødsel(
        fødselsdato: LocalDate = LocalDate.now(),
        metadata: EndringsMetadata = mockEndringsMetadata(),
        folkeregisterMetadata: FolkeregisterMetadata = mockFolkeregisterMetadata(),
    ): Fødsel {
        return Fødsel(
            foedselsdato = fødselsdato,
            metadata = metadata,
            folkeregistermetadata = folkeregisterMetadata,
        )
    }

    fun mockAdressebeskyttelse(
        gradering: AdressebeskyttelseGradering = AdressebeskyttelseGradering.UGRADERT,
        metadata: EndringsMetadata = mockEndringsMetadata(),
        folkeregisterMetadata: FolkeregisterMetadata = mockFolkeregisterMetadata(),
    ): Adressebeskyttelse = Adressebeskyttelse(
        gradering = gradering,
        metadata = metadata,
        folkeregistermetadata = folkeregisterMetadata,
    )

    fun mockSøkerRespons(forelderBarnRelasjon: List<ForelderBarnRelasjon> = emptyList()): SøkerRespons {
        return SøkerRespons(
            hentPerson = SøkerFraPDL(
                navn = listOf(mockNavn()),
                adressebeskyttelse = emptyList(),
                forelderBarnRelasjon = forelderBarnRelasjon,
                doedsfall = emptyList(),
                foedselsdato = listOf(mockFødsel()),
            ),
            hentGeografiskTilknytning = GeografiskTilknytning(
                gtType = "KOMMUNE",
                gtKommune = "1122",
                gtBydel = null,
                gtLand = null,
            ),
        )
    }

    fun mockForelderBarnRelasjon(
        rolle: ForelderBarnRelasjonRolle = ForelderBarnRelasjonRolle.BARN,
        ident: String = testBarnFødselsnummer,
    ): ForelderBarnRelasjon {
        return ForelderBarnRelasjon(
            relatertPersonsRolle = rolle,
            relatertPersonsIdent = ident,
            folkeregistermetadata = mockFolkeregisterMetadata(),
            metadata = mockEndringsMetadata(),
        )
    }

    private fun mockSøkersBarn(
        barn: List<SøkersBarnFraPDLBolk> = listOf(mockSøkersBarnFraPdl()),
    ): SøkersBarnRespons =
        SøkersBarnRespons(
            hentPersonBolk = barn,
        )

    private fun mockSøkersBarnFraPdl(
        ident: String = testBarnFødselsnummer,
        navn: List<Navn> = listOf(mockNavn()),
        adressebeskyttelse: List<Adressebeskyttelse> = listOf(mockAdressebeskyttelse()),
        fødsel: List<Fødsel> = listOf(mockFødsel()),
        dødsfall: List<Dødsfall> = emptyList(),
    ): SøkersBarnFraPDLBolk =
        SøkersBarnFraPDLBolk(
            ident = ident,
            person = SøkersBarnFraPDL(
                navn = navn,
                adressebeskyttelse = adressebeskyttelse,
                foedselsdato = fødsel,
                doedsfall = dødsfall,
            ),
            code = PdlPersonBolkCode.OK,
        )

    private val søkersBarnDefaultMock: SøkersBarnRespons = mockSøkersBarn()

    private val fødselsdatoUnder16År = LocalDate.now().minusYears(16).plusDays(1)
    private val søkersBarnUnder16År: SøkersBarnRespons = mockSøkersBarn(
        barn = listOf(
            mockSøkersBarnFraPdl(
                fødsel = listOf(mockFødsel(fødselsdato = fødselsdatoUnder16År)),
            ),
        ),
    )

    private val fødselsdatoOver16År = LocalDate.now().minusYears(16)
    private val søkersBarnOver16År: SøkersBarnRespons = mockSøkersBarn(
        barn = listOf(
            mockSøkersBarnFraPdl(
                fødsel = listOf(mockFødsel(fødselsdato = fødselsdatoOver16År)),
            ),
        ),
    )

    private val barnMedStrengtFortrolig: SøkersBarnRespons = mockSøkersBarn(
        barn = listOf(
            mockSøkersBarnFraPdl(
                adressebeskyttelse = listOf(mockAdressebeskyttelse(gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG)),
            ),
        ),
    )

    private val barnMedFortrolig: SøkersBarnRespons = mockSøkersBarn(
        barn = listOf(
            mockSøkersBarnFraPdl(
                adressebeskyttelse = listOf(mockAdressebeskyttelse(gradering = AdressebeskyttelseGradering.FORTROLIG)),
            ),
        ),
    )

    private val barnMedStrengtFortroligUtland: SøkersBarnRespons = mockSøkersBarn(
        barn = listOf(
            mockSøkersBarnFraPdl(
                adressebeskyttelse = listOf(mockAdressebeskyttelse(gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND)),
            ),
        ),
    )

    private val barnMedUgradert: SøkersBarnRespons = mockSøkersBarn(
        barn = listOf(
            mockSøkersBarnFraPdl(
                adressebeskyttelse = listOf(mockAdressebeskyttelse(gradering = AdressebeskyttelseGradering.UGRADERT)),
            ),
        ),
    )

    private val mockedPdlClient = mockk<PdlClient>()

    private val pdlService = PdlService(
        pdlClient = mockedPdlClient,
    )

    @Test
    fun `ved kall på hentPersonaliaMedBarn skal man hente data om søker med oppgitt fnr med tokenX, etterfulgt av å hente data om søkers barn med client credentials`() {
        val token = "token"
        runBlocking {
            mockedPdlClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns
                    mockSøkerRespons(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    )
                coEvery { mock.fetchBarn(any(), any()) } returns søkersBarnDefaultMock
            }
            pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            coVerify { mockedPdlClient.fetchSøker(testFødselsnummer, token, "test") }
            coVerify { mockedPdlClient.fetchBarn(listOf(testBarnFødselsnummer), "test") }
        }
    }

    @Test
    fun `ved kall på hentPersonaliaMedBarn skal man ikke hente data om barn dersom det ikke fantes noen barn i søkerens forelderBarnRelasjon`() {
        val token = "token"
        runBlocking {
            mockedPdlClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns mockSøkerRespons()
            }
            pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            coVerify { mockedPdlClient.fetchSøker(testFødselsnummer, token, "test") }
            coVerify(exactly = 0) { mockedPdlClient.fetchBarn(any(), any()) }
        }
    }

    @Test
    fun `når fetchSøker med tokenx mot PDL feiler, kastes en IllegalStateExcepiton`() {
        val token = "token"
        assertThrows<IllegalStateException> {
            mockedPdlClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } throws IllegalStateException("verify in test")
            }
            runBlocking {
                pdlService.hentPersonaliaMedBarn(
                    fødselsnummer = testFødselsnummer,
                    subjectToken = token,
                    callId = "test",
                )
            }
        }.message shouldBe "verify in test"
    }

    @Test
    fun `hentPersonaliaMedBarn skal ikke returnere barn fra forelderBarnRelasjon som er over 16 år`() {
        val token = "token"
        runBlocking {
            mockedPdlClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns
                    mockSøkerRespons(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    )
                coEvery { mock.fetchBarn(any(), any()) } returns søkersBarnOver16År
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            assertTrue(person.barn.isEmpty())
        }
    }

    @Test
    fun `hentPersonaliaMedBarn skal returnere barn fra forelderBarnRelasjon som er under 16 år`() {
        val token = "token"
        runBlocking {
            mockedPdlClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns
                    mockSøkerRespons(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    )
                coEvery { mock.fetchBarn(any(), any()) } returns søkersBarnUnder16År
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            assertEquals(person.barn.size, 1)
            assertEquals(
                person.barn[0].fødselsdato,
                søkersBarnUnder16År.toPersoner().first()!!.fødselsdato,
            )
        }
    }

    @Test
    fun `hentPersonaliaMedBarn skal kun returnere fødselsdato på barn som er STRENGT_FORTROLIG`() {
        val token = "token"
        runBlocking {
            mockedPdlClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns
                    mockSøkerRespons(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    )
                coEvery { mock.fetchBarn(any(), any()) } returns barnMedStrengtFortrolig
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            assertEquals(person.barn.size, 1)

            val barn = person.barn[0]
            assertNull(barn.fornavn)
            assertNull(barn.mellomnavn)
            assertNull(barn.etternavn)
            assertNotNull(barn.fødselsdato)
        }
    }

    @Test
    fun `hentPersonaliaMedBarn skal kun returnere fødselsdato på barn som er FORTROLIG`() {
        val token = "token"
        runBlocking {
            mockedPdlClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns
                    mockSøkerRespons(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    )
                coEvery { mock.fetchBarn(any(), any()) } returns barnMedFortrolig
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            assertEquals(person.barn.size, 1)

            val barn = person.barn[0]
            assertNull(barn.fornavn)
            assertNull(barn.mellomnavn)
            assertNull(barn.etternavn)
            assertNotNull(barn.fødselsdato)
        }
    }

    @Test
    fun `hentPersonaliaMedBarn skal kun returnere fødselsdato på barn som er STRENGT_FORTROLIG_UTLAND`() {
        val token = "token"
        runBlocking {
            mockedPdlClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns
                    mockSøkerRespons(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    )
                coEvery { mock.fetchBarn(any(), any()) } returns barnMedStrengtFortroligUtland
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            assertEquals(person.barn.size, 1)

            val barn = person.barn[0]
            assertNull(barn.fornavn)
            assertNull(barn.mellomnavn)
            assertNull(barn.etternavn)
            assertNotNull(barn.fødselsdato)
        }
    }

    @Test
    fun `hentPersonaliaMedBarn skal returnere barn med fornavn, mellomnavn og etternavn, når barnet er UGRADERT`() {
        val token = "token"
        runBlocking {
            mockedPdlClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns
                    mockSøkerRespons(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    )
                coEvery { mock.fetchBarn(any(), any()) } returns barnMedUgradert
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            assertEquals(person.barn.size, 1)

            val barn = person.barn[0]
            assertNotNull(barn.fornavn)
            assertNotNull(barn.mellomnavn)
            assertNotNull(barn.etternavn)
            assertNotNull(barn.fødselsdato)
        }
    }

    private fun SøkersBarnFraPDLBolk.toBarnDTO(): BarnDTO {
        return BarnDTO(
            fnr = ident,
            fødselsdato = person?.foedselsdato?.first()!!.foedselsdato,
            fornavn = person.navn.first().fornavn,
            mellomnavn = person.navn.first().mellomnavn,
            etternavn = person.navn.first().etternavn,
        )
    }

    @Test
    fun `hentPersonaliaMedBarn skal filtrere vekk barn som er over 16 år på styrendeDato`() {
        val token = "token"
        val startdato = LocalDate.of(2020, 1, 1)

        val barnOver16ÅrPåTiltaksstartdatoIdent = "07090506506"
        val barnOver16ÅrPåTiltaksstartdato = mockSøkersBarnFraPdl(
            ident = barnOver16ÅrPåTiltaksstartdatoIdent,
            fødsel = listOf(mockFødsel(fødselsdato = startdato.minusYears(16).minusDays(1))),
        )
        val barnSomFyller16ÅrPåTiltaksstartdatoIdent = "09052267207"
        val barnSomFyller16ÅrPåTiltaksstartdato = mockSøkersBarnFraPdl(
            ident = barnSomFyller16ÅrPåTiltaksstartdatoIdent,
            fødsel = listOf(mockFødsel(fødselsdato = startdato.minusYears(16))),
        )
        val barnUnder16ÅrPåTiltaksstartdatoIdent = "05106020371"
        val barnUnder16ÅrPåTiltaksstartdato = mockSøkersBarnFraPdl(
            ident = barnUnder16ÅrPåTiltaksstartdatoIdent,
            fødsel = listOf(
                mockFødsel(
                    fødselsdato = startdato.minusYears(16).plusDays(1),
                ),
            ),
        )
        val barn2Under16ÅrPåTiltaksstartdatoIdent = "26052341395"
        val barn2Under16ÅrPåTiltaksstartdato = mockSøkersBarnFraPdl(
            ident = barn2Under16ÅrPåTiltaksstartdatoIdent,
            fødsel = listOf(
                mockFødsel(
                    fødselsdato = startdato.minusYears(16).plusDays(2),
                ),
            ),
        )

        val forventetResponse = mockSøkersBarn(
            barn = listOf(
                barnOver16ÅrPåTiltaksstartdato,
                barnSomFyller16ÅrPåTiltaksstartdato,
                barnUnder16ÅrPåTiltaksstartdato,
                barn2Under16ÅrPåTiltaksstartdato,
            ),
        )

        runBlocking {
            mockedPdlClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns
                    mockSøkerRespons(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(ident = barnOver16ÅrPåTiltaksstartdatoIdent),
                            mockForelderBarnRelasjon(ident = barnSomFyller16ÅrPåTiltaksstartdatoIdent),
                            mockForelderBarnRelasjon(ident = barnUnder16ÅrPåTiltaksstartdatoIdent),
                            mockForelderBarnRelasjon(ident = barn2Under16ÅrPåTiltaksstartdatoIdent),
                        ),
                    )
                coEvery {
                    mock.fetchBarn(
                        listOf(
                            barnOver16ÅrPåTiltaksstartdatoIdent,
                            barnSomFyller16ÅrPåTiltaksstartdatoIdent,
                            barnUnder16ÅrPåTiltaksstartdatoIdent,
                            barn2Under16ÅrPåTiltaksstartdatoIdent,
                        ),
                        any(),
                    )
                } returns forventetResponse
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                styrendeDato = startdato,
                subjectToken = token,
                callId = "test",
            )

            person.barn.size shouldBe 2
            person.barn shouldBe listOf(
                barnUnder16ÅrPåTiltaksstartdato.toBarnDTO(),
                barn2Under16ÅrPåTiltaksstartdato.toBarnDTO(),
            )
        }
    }
}
