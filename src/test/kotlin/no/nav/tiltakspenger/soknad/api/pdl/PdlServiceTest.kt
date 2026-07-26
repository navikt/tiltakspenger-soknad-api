package no.nav.tiltakspenger.soknad.api.pdl

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata
import no.nav.tiltakspenger.libs.httpklient.HttpKlientTidsstempler
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.libs.personklient.pdl.dto.PdlPersonBolkCode
import no.nav.tiltakspenger.soknad.api.pdl.client.KanIkkeHentePerson
import no.nav.tiltakspenger.soknad.api.pdl.client.PdlClient
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.Dødsfall
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.Endring
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.EndringsMetadata
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.FolkeregisterMetadata
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.ForelderBarnRelasjon
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.ForelderBarnRelasjonRolle
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.Fødsel
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.GeografiskTilknytning
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.Kilde
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.Navn
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkerFraPDL
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkerRespons
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkersBarnFraPDL
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkersBarnFraPDLBolk
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkersBarnRespons
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.avklarFødsel
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.avklarNavn
import no.nav.tiltakspenger.soknad.api.pdl.routes.dto.BarnDTO
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class PdlServiceTest {
    val testFødselsnummer = "02058938710"
    val testBarnFødselsnummer = "21062002856"
    private val dagensDato = LocalDate.now(fixedClock)

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
        fødselsdato: LocalDate = LocalDate.now(fixedClock),
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

    fun mockSøkerResponsRight(forelderBarnRelasjon: List<ForelderBarnRelasjon> = emptyList()) =
        mockSøkerRespons(forelderBarnRelasjon).right()

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

    private val fødselsdatoUnder16År = dagensDato.minusYears(16).plusDays(1)
    private val søkersBarnUnder16År: SøkersBarnRespons = mockSøkersBarn(
        barn = listOf(
            mockSøkersBarnFraPdl(
                fødsel = listOf(mockFødsel(fødselsdato = fødselsdatoUnder16År)),
            ),
        ),
    )

    private val fødselsdatoOver16År = dagensDato.minusYears(16)
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
        clock = fixedClock,
        sikkerlogg = Sikkerlogg,
    )

    @Test
    fun `ved kall på hentPersonaliaMedBarn skal man hente data om søker med oppgitt fnr med tokenX, etterfulgt av å hente data om søkers barn med client credentials`() {
        val token = "token"
        runBlocking {
            mockedPdlClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any()) } returns
                    mockSøkerResponsRight(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    )
                coEvery { mock.fetchBarn(any()) } returns søkersBarnDefaultMock.right()
            }
            pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            coVerify { mockedPdlClient.fetchSøker(testFødselsnummer, token) }
            coVerify { mockedPdlClient.fetchBarn(nonEmptyListOf(testBarnFødselsnummer)) }
        }
    }

    @Test
    fun `ved kall på hentPersonaliaMedBarn skal man ikke hente data om barn dersom det ikke fantes noen barn i søkerens forelderBarnRelasjon`() {
        val token = "token"
        runBlocking {
            mockedPdlClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any()) } returns mockSøkerRespons().right()
            }
            pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            )
            coVerify { mockedPdlClient.fetchSøker(testFødselsnummer, token) }
            coVerify(exactly = 0) { mockedPdlClient.fetchBarn(any()) }
        }
    }

    @Test
    fun `når fetchSøker mot PDL feiler, forplanter feilen seg ut til kalleren`() {
        runBlocking {
            coEvery { mockedPdlClient.fetchSøker(any(), any()) } returns
                KanIkkeHentePerson.KallFeilet(HttpKlientError.UventetStatus(503, "pdl er nede", tomMetadata())).left()

            val feil = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = "token",
                callId = "test",
            ).leftOrNull()!!

            feil.shouldBeInstanceOf<KanIkkeHentePerson.KallFeilet>()
                .feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 503
        }
    }

    @Test
    fun `når bolkoppslaget av barna feiler, forplanter feilen seg ut til kalleren`() {
        runBlocking {
            coEvery { mockedPdlClient.fetchSøker(any(), any()) } returns
                mockSøkerResponsRight(forelderBarnRelasjon = listOf(mockForelderBarnRelasjon()))
            coEvery { mockedPdlClient.fetchBarn(any()) } returns KanIkkeHentePerson.ResponsManglerData(tomMetadata()).left()

            pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = "token",
                callId = "test",
            ).leftOrNull()!!.shouldBeInstanceOf<KanIkkeHentePerson.ResponsManglerData>()
        }
    }

    @Test
    fun `når hentNavnForFnr feiler, forplanter feilen seg ut til jobben`() {
        runBlocking {
            coEvery { mockedPdlClient.fetchSøkerSystembruker(any()) } returns
                KanIkkeHentePerson.GraphQLFeil(nonEmptyListOf("Fant ikke person"), tomMetadata()).left()

            pdlService.hentNavnForFnr(Fnr.fromString(testFødselsnummer), CorrelationId.generate())
                .leftOrNull()!!.shouldBeInstanceOf<KanIkkeHentePerson.GraphQLFeil>()
        }
    }

    @Test
    fun `når hentAdressebeskyttelse feiler, forplanter feilen seg ut til ruta`() {
        runBlocking {
            coEvery { mockedPdlClient.fetchSøker(any(), any()) } returns
                KanIkkeHentePerson.KallFeilet(HttpKlientError.UventetStatus(500, "nede", tomMetadata())).left()

            pdlService.hentAdressebeskyttelse(testFødselsnummer, "token", "test")
                .leftOrNull()!!.shouldBeInstanceOf<KanIkkeHentePerson.KallFeilet>()
        }
    }

    /** [HttpKlientMetadata] har bevisst ingen defaults, så testene fyller alle feltene eksplisitt. */
    private fun tomMetadata() = HttpKlientMetadata(
        rawRequestString = "",
        rawResponseString = null,
        requestHeaders = emptyMap(),
        responseHeaders = emptyMap(),
        statusCode = null,
        attempts = 1,
        attemptDurations = emptyList(),
        totalDuration = kotlin.time.Duration.ZERO,
        tidsstempler = HttpKlientTidsstempler.INGEN,
    )

    @Test
    fun `hentPersonaliaMedBarn skal ikke returnere barn fra forelderBarnRelasjon som er over 16 år`() {
        val token = "token"
        runBlocking {
            mockedPdlClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any()) } returns
                    mockSøkerResponsRight(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    )
                coEvery { mock.fetchBarn(any()) } returns søkersBarnOver16År.right()
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            ).getOrFail()
            assertTrue(person.barn.isEmpty())
        }
    }

    @Test
    fun `hentPersonaliaMedBarn skal returnere barn fra forelderBarnRelasjon som er under 16 år`() {
        val token = "token"
        runBlocking {
            mockedPdlClient.also { mock ->
                coEvery { mock.fetchSøker(any(), any()) } returns
                    mockSøkerResponsRight(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    )
                coEvery { mock.fetchBarn(any()) } returns søkersBarnUnder16År.right()
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            ).getOrFail()
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
                coEvery { mock.fetchSøker(any(), any()) } returns
                    mockSøkerResponsRight(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    )
                coEvery { mock.fetchBarn(any()) } returns barnMedStrengtFortrolig.right()
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            ).getOrFail()
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
                coEvery { mock.fetchSøker(any(), any()) } returns
                    mockSøkerResponsRight(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    )
                coEvery { mock.fetchBarn(any()) } returns barnMedFortrolig.right()
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            ).getOrFail()
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
                coEvery { mock.fetchSøker(any(), any()) } returns
                    mockSøkerResponsRight(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    )
                coEvery { mock.fetchBarn(any()) } returns barnMedStrengtFortroligUtland.right()
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            ).getOrFail()
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
                coEvery { mock.fetchSøker(any(), any()) } returns
                    mockSøkerResponsRight(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(),
                        ),
                    )
                coEvery { mock.fetchBarn(any()) } returns barnMedUgradert.right()
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                subjectToken = token,
                callId = "test",
            ).getOrFail()
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
                coEvery { mock.fetchSøker(any(), any()) } returns
                    mockSøkerResponsRight(
                        forelderBarnRelasjon = listOf(
                            mockForelderBarnRelasjon(ident = barnOver16ÅrPåTiltaksstartdatoIdent),
                            mockForelderBarnRelasjon(ident = barnSomFyller16ÅrPåTiltaksstartdatoIdent),
                            mockForelderBarnRelasjon(ident = barnUnder16ÅrPåTiltaksstartdatoIdent),
                            mockForelderBarnRelasjon(ident = barn2Under16ÅrPåTiltaksstartdatoIdent),
                        ),
                    )
                coEvery {
                    mock.fetchBarn(
                        nonEmptyListOf(
                            barnOver16ÅrPåTiltaksstartdatoIdent,
                            barnSomFyller16ÅrPåTiltaksstartdatoIdent,
                            barnUnder16ÅrPåTiltaksstartdatoIdent,
                            barn2Under16ÅrPåTiltaksstartdatoIdent,
                        ),
                    )
                } returns forventetResponse.right()
            }
            val person = pdlService.hentPersonaliaMedBarn(
                fødselsnummer = testFødselsnummer,
                styrendeDato = startdato,
                subjectToken = token,
                callId = "test",
            ).getOrFail()

            person.barn.size shouldBe 2
            person.barn shouldBe listOf(
                barnUnder16ÅrPåTiltaksstartdato.toBarnDTO(),
                barn2Under16ÅrPåTiltaksstartdato.toBarnDTO(),
            )
        }
    }

    @Test
    fun `hentAdressebeskyttelse henter søker og returnerer graderingen`() {
        runBlocking {
            coEvery { mockedPdlClient.fetchSøker(any(), any()) } returns mockSøkerRespons().right()

            val gradering = pdlService.hentAdressebeskyttelse(
                fødselsnummer = testFødselsnummer,
                subjectToken = "token",
                callId = "test",
            ).getOrFail()

            gradering shouldBe AdressebeskyttelseGradering.UGRADERT
        }
    }

    @Test
    fun `hentNavnForFnr henter søker som systembruker og returnerer navnet`() {
        runBlocking {
            coEvery { mockedPdlClient.fetchSøkerSystembruker(any()) } returns mockSøkerRespons().right()

            val navn = pdlService.hentNavnForFnr(
                fnr = Fnr.fromString(testFødselsnummer),
                correlationId = CorrelationId.generate(),
            ).getOrFail()

            navn shouldBe no.nav.tiltakspenger.soknad.api.pdl.Navn(fornavn = "foo", mellomnavn = "baz", etternavn = "bar")
        }
    }

    @Test
    fun `toPerson kaster når søker er registrert som død i PDL`() {
        val respons = mockSøkerRespons().let {
            it.copy(hentPerson = it.hentPerson!!.copy(doedsfall = listOf(Dødsfall(doedsdato = LocalDate.of(2020, 1, 1)))))
        }

        shouldThrow<IllegalStateException> { respons.toPerson(Fnr.fromString(testFødselsnummer)) }
    }

    @Test
    fun `avklarNavn kaster når alle navnene har udokumentert kilde`() {
        val udokumentertNavn = mockNavn().copy(
            metadata = EndringsMetadata(
                endringer = listOf(
                    Endring(
                        kilde = Kilde.BRUKER_SELV,
                        registrert = LocalDateTime.of(2020, 1, 1, 12, 0),
                        registrertAv = "bruker",
                        systemkilde = "test",
                        type = "OPPRETT",
                    ),
                ),
                master = Kilde.PDL,
            ),
        )

        shouldThrow<IllegalStateException> { avklarNavn(listOf(udokumentertNavn)) }
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
                    ident = testBarnFødselsnummer,
                    person = null,
                    code = PdlPersonBolkCode.NOT_FOUND,
                ),
            ),
        )

        respons.toPersoner() shouldBe listOf(null)
    }
}
