package no.nav.tiltakspenger.soknad.api.pdl

import io.ktor.server.config.ApplicationConfig
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
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
                    forelderBarnRelasjon = forelderBarnRelasjon,
                    doedsfall = emptyList(),
                    foedsel = listOf(mockFødsel()),
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
                    foedsel = fødsel,
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
                            mockForelderBarnRelasjon(),
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
            coVerify { mockedCredentialsClient.fetchBarn(testBarnFødselsnummer, "test") }
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
}
