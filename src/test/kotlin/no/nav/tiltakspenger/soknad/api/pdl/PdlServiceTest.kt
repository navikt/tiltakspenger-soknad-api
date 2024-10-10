package no.nav.tiltakspenger.soknad.api.pdl

import io.kotest.matchers.shouldBe
import io.ktor.server.config.ApplicationConfig
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class PdlServiceTest {
    val testFødselsnummer = "123"
    val testBarnFødselsnummer = "456"

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
            data = SøkerFraPDLRespons(
                hentPerson = SøkerFraPDL(
                    navn = listOf(mockNavn()),
                    adressebeskyttelse = emptyList(),
                    // KEW Denne er midlertidig fjernet siden den ikke brukes per nå: https://trello.com/c/NQzRwuNf/1129-fjern-foreldre-barn-relasjon-i-personquery-mot-pdl
                    // forelderBarnRelasjon = forelderBarnRelasjon,
                    doedsfall = emptyList(),
                    foedselsdato = listOf(mockFødsel()),
                ),
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
        navn: List<Navn> = listOf(mockNavn()),
        adressebeskyttelse: List<Adressebeskyttelse> = listOf(mockAdressebeskyttelse()),
        fødsel: List<Fødsel> = listOf(mockFødsel()),
        dødsfall: List<Dødsfall> = emptyList(),
    ): SøkersBarnRespons =
        SøkersBarnRespons(
            data = SøkersBarnFraPDLRespons(
                hentPerson = SøkersBarnFraPDL(
                    navn = navn,
                    adressebeskyttelse = adressebeskyttelse,
                    foedselsdato = fødsel,
                    doedsfall = dødsfall,
                ),
            ),
        )

    private val søkersBarnDefaultMock: SøkersBarnRespons = mockSøkersBarn()

    private val fødselsdatoUnder16År = LocalDate.now().minusYears(16).plusDays(1)
    private val søkersBarnUnder16År: SøkersBarnRespons = mockSøkersBarn(
        fødsel = listOf(mockFødsel(fødselsdato = fødselsdatoUnder16År)),
    )

    private val fødselsdatoOver16År = LocalDate.now().minusYears(16)
    private val søkersBarnOver16År: SøkersBarnRespons = mockSøkersBarn(
        fødsel = listOf(mockFødsel(fødselsdato = fødselsdatoOver16År)),
    )

    private val barnMedStrengtFortrolig: SøkersBarnRespons = mockSøkersBarn(
        adressebeskyttelse = listOf(mockAdressebeskyttelse(gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG)),
    )

    private val barnMedFortrolig: SøkersBarnRespons = mockSøkersBarn(
        adressebeskyttelse = listOf(mockAdressebeskyttelse(gradering = AdressebeskyttelseGradering.FORTROLIG)),
    )

    private val barnMedStrengtFortroligUtland: SøkersBarnRespons = mockSøkersBarn(
        adressebeskyttelse = listOf(mockAdressebeskyttelse(gradering = AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND)),
    )

    private val barnMedUgradert: SøkersBarnRespons = mockSøkersBarn(
        adressebeskyttelse = listOf(mockAdressebeskyttelse(gradering = AdressebeskyttelseGradering.UGRADERT)),
    )

    private val mockedTokenXClient = mockk<PdlClientTokenX>()
    private val mockedCredentialsClient = mockk<PdlCredentialsClient>()

    private val pdlService = PdlService(
        ApplicationConfig("application.test.conf"),
        pdlClientTokenX = mockedTokenXClient,
        pdlClientCredentials = mockedCredentialsClient,
    )

    @Test
    fun `ved kall på hentPersonaliaMedBarn skal man hente data om søker med oppgitt fnr med tokenX, etterfulgt av å hente data om søkers barn med client credentials`() {
        val token = "token"
        runBlocking {
            mockedCredentialsClient.also { mock ->
                coEvery { mock.fetchBarn(any(), any()) } returns Result.success(søkersBarnDefaultMock)
            }
            mockedTokenXClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns Result.success(
                    mockSøkerRespons(
                        forelderBarnRelasjon = listOf(
//                            mockForelderBarnRelasjon(),
                        ),
                    ),
                )
            }
            pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            coVerify { mockedTokenXClient.fetchSøker(testFødselsnummer, token, "test") }
//            coVerify { mockedCredentialsClient.fetchBarn(testBarnFødselsnummer, "test") }
        }
    }

    @Test
    fun `ved kall på hentPersonaliaMedBarn skal man ikke hente data om barn dersom det ikke fantes noen barn i søkerens forelderBarnRelasjon`() {
        val token = "token"
        runBlocking {
            mockedTokenXClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns Result.success(mockSøkerRespons())
            }
            pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            coVerify { mockedTokenXClient.fetchSøker(testFødselsnummer, token, "test") }
            coVerify(exactly = 0) { mockedCredentialsClient.fetchBarn(any(), any()) }
        }
    }

    @Test
    fun `når fetchSøker med tokenx mot PDL feiler, kastes en IllegalStateExcepiton`() {
        val token = "token"
        assertThrows<IllegalStateException> {
            mockedTokenXClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns Result.failure(Exception())
            }
            runBlocking {
                pdlService.hentPersonaliaMedBarn(
                    fødselsnummer = testFødselsnummer,
                    subjectToken = token,
                    callId = "test",
                )
            }
        }.also {
            assertEquals("Noe gikk galt under kall til PDL", it.message)
        }
    }

    @Test
    fun `hentPersonaliaMedBarn skal ikke returnere barn fra forelderBarnRelasjon som er over 16 år`() {
        val token = "token"
        runBlocking {
            mockedTokenXClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns Result.success(
                    mockSøkerRespons(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    ),
                )
            }
            mockedCredentialsClient.also { mock ->
                coEvery { mock.fetchBarn(any(), any()) } returns Result.success(søkersBarnOver16År)
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            assertTrue(person.barn.isEmpty())
        }
    }

    @Disabled // Kew, denne er disablet frem til forelderBarnRelasjon skal brukes igjen
    @Test
    fun `hentPersonaliaMedBarn skal returnere barn fra forelderBarnRelasjon som er under 16 år`() {
        val token = "token"
        runBlocking {
            mockedTokenXClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns Result.success(
                    mockSøkerRespons(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    ),
                )
            }
            mockedCredentialsClient.also { mock ->
                coEvery { mock.fetchBarn(any(), any()) } returns Result.success(søkersBarnUnder16År)
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            assertTrue(person.barn.size == 1)
            assertEquals(person.barn.get(0).fødselsdato, søkersBarnUnder16År.toPerson().fødselsdato)
        }
    }

    @Disabled // Kew, denne er disablet frem til forelderBarnRelasjon skal brukes igjen
    @Test
    fun `hentPersonaliaMedBarn skal kun returnere fødselsdato på barn som er STRENGT_FORTROLIG`() {
        val token = "token"
        runBlocking {
            mockedTokenXClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns Result.success(
                    mockSøkerRespons(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    ),
                )
            }
            mockedCredentialsClient.also { mock ->
                coEvery { mock.fetchBarn(any(), any()) } returns Result.success(barnMedStrengtFortrolig)
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            assertTrue(person.barn.size == 1)

            val barn = person.barn.get(0)
            assertNull(barn.fornavn)
            assertNull(barn.mellomnavn)
            assertNull(barn.etternavn)
            assertNotNull(barn.fødselsdato)
        }
    }

    @Disabled // Kew, denne er disablet frem til forelderBarnRelasjon skal brukes igjen
    @Test
    fun `hentPersonaliaMedBarn skal kun returnere fødselsdato på barn som er FORTROLIG`() {
        val token = "token"
        runBlocking {
            mockedTokenXClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns Result.success(
                    mockSøkerRespons(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    ),
                )
            }
            mockedCredentialsClient.also { mock ->
                coEvery { mock.fetchBarn(any(), any()) } returns Result.success(barnMedFortrolig)
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            assertTrue(person.barn.size == 1)

            val barn = person.barn.get(0)
            assertNull(barn.fornavn)
            assertNull(barn.mellomnavn)
            assertNull(barn.etternavn)
            assertNotNull(barn.fødselsdato)
        }
    }

    @Disabled // Kew, denne er disablet frem til forelderBarnRelasjon skal brukes igjen
    @Test
    fun `hentPersonaliaMedBarn skal kun returnere fødselsdato på barn som er STRENGT_FORTROLIG_UTLAND`() {
        val token = "token"
        runBlocking {
            mockedTokenXClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns Result.success(
                    mockSøkerRespons(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    ),
                )
            }
            mockedCredentialsClient.also { mock ->
                coEvery { mock.fetchBarn(any(), any()) } returns Result.success(barnMedStrengtFortroligUtland)
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            assertTrue(person.barn.size == 1)

            val barn = person.barn.get(0)
            assertNull(barn.fornavn)
            assertNull(barn.mellomnavn)
            assertNull(barn.etternavn)
            assertNotNull(barn.fødselsdato)
        }
    }

    @Disabled // Kew, denne er disablet frem til forelderBarnRelasjon skal brukes igjen
    @Test
    fun `hentPersonaliaMedBarn skal returnere barn med fornavn, mellomnavn og etternavn, når barnet er UGRADERT`() {
        val token = "token"
        runBlocking {
            mockedTokenXClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns Result.success(
                    mockSøkerRespons(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    ),
                )
            }
            mockedCredentialsClient.also { mock ->
                coEvery { mock.fetchBarn(any(), any()) } returns Result.success(barnMedUgradert)
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            assertTrue(person.barn.size == 1)

            val barn = person.barn.get(0)
            assertNotNull(barn.fornavn)
            assertNotNull(barn.mellomnavn)
            assertNotNull(barn.etternavn)
            assertNotNull(barn.fødselsdato)
        }
    }

    fun SøkersBarnRespons.toBarnDTO(): BarnDTO = BarnDTO(
        fødselsdato = this.data?.hentPerson?.foedselsdato?.first()!!.foedselsdato,
        fornavn = this.data?.hentPerson?.navn?.first()!!.fornavn,
        mellomnavn = this.data?.hentPerson?.navn?.first()!!.mellomnavn,
        etternavn = this.data?.hentPerson?.navn?.first()!!.etternavn,
    )

    @Disabled // Kew, denne er disablet frem til forelderBarnRelasjon skal brukes igjen
    @Test
    fun `hentPersonaliaMedBarn skal filtrere vekk barn som er over 16 år på styrendeDato`() {
        val token = "token"
        val startdato = LocalDate.of(2020, 1, 1)

        val barnOver16ÅrPåTiltaksstartdato = mockSøkersBarn(fødsel = listOf(mockFødsel(fødselsdato = startdato.minusYears(16).minusDays(1))))
        val barnSomFyller16ÅrPåTiltaksstartdato = mockSøkersBarn(fødsel = listOf(mockFødsel(fødselsdato = startdato.minusYears(16))))
        val barnUnder16ÅrPåTiltaksstartdato = mockSøkersBarn(fødsel = listOf(mockFødsel(fødselsdato = startdato.minusYears(16).plusDays(1))))
        val barn2Under16ÅrPåTiltaksstartdato = mockSøkersBarn(fødsel = listOf(mockFødsel(fødselsdato = startdato.minusYears(16).plusDays(2))))

        runBlocking {
            mockedTokenXClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any(), any()) } returns Result.success(
                    mockSøkerRespons(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(ident = "barnOver16År"),
                            mockForelderBarnRelasjon(ident = "barnSomFyller16År"),
                            mockForelderBarnRelasjon(ident = "barnUnder16År"),
                            mockForelderBarnRelasjon(ident = "barnUnder16År2"),
                        ),
                    ),
                )
            }
            mockedCredentialsClient.also { mock ->
                coEvery { mock.fetchBarn("barnOver16År", any()) } returns Result.success(barnOver16ÅrPåTiltaksstartdato)
                coEvery { mock.fetchBarn("barnSomFyller16År", any()) } returns Result.success(barnSomFyller16ÅrPåTiltaksstartdato)
                coEvery { mock.fetchBarn("barnUnder16År", any()) } returns Result.success(barnUnder16ÅrPåTiltaksstartdato)
                coEvery { mock.fetchBarn("barnUnder16År2", any()) } returns Result.success(barn2Under16ÅrPåTiltaksstartdato)
            }

            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                styrendeDato = startdato,
                subjectToken = token,
                callId = "test",
            )

            person.barn.size shouldBe 2
            person.barn shouldBe listOf(barnUnder16ÅrPåTiltaksstartdato.toBarnDTO(), barn2Under16ÅrPåTiltaksstartdato.toBarnDTO())
        }
    }
}
