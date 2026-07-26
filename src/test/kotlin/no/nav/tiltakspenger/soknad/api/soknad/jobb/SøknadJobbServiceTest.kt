package no.nav.tiltakspenger.soknad.api.soknad.jobb

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata
import no.nav.tiltakspenger.libs.httpklient.HttpKlientTidsstempler
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.soknad.api.db.testDatabaseManager
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivClient
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivService
import no.nav.tiltakspenger.soknad.api.dokarkiv.JOURNALFORENDE_ENHET_AUTOMATISK_BEHANDLING
import no.nav.tiltakspenger.soknad.api.dokarkiv.KunneIkkeJournalføre
import no.nav.tiltakspenger.soknad.api.pdf.PdfService
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.pdl.client.KanIkkeHentePerson
import no.nav.tiltakspenger.soknad.api.saksbehandlingApi.SaksbehandlingApiKlient
import no.nav.tiltakspenger.soknad.api.soknad.Applikasjonseier
import no.nav.tiltakspenger.soknad.api.soknad.SøknadRepo
import no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforing.JournalforingService
import no.nav.tiltakspenger.soknad.api.soknad.validering.søknad
import no.nav.tiltakspenger.soknad.api.util.genererMottattSøknadForTest
import no.nav.tiltakspenger.soknad.api.util.getTestNavnFraPdl
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import org.junit.jupiter.api.Test

class SøknadJobbServiceTest {
    /** [HttpKlientMetadata] har bevisst ingen defaults, så testene fyller alle feltene eksplisitt. */
    private fun tomMetadata() = HttpKlientMetadata(
        rawRequestString = "",
        rawResponseString = null,
        requestHeaders = emptyMap(),
        responseHeaders = emptyMap(),
        statusCode = 500,
        attempts = 1,
        attemptDurations = emptyList(),
        totalDuration = kotlin.time.Duration.ZERO,
        tidsstempler = HttpKlientTidsstempler.INGEN,
    )

    private val pdlService = mockk<PdlService>()
    private val pdfService = mockk<PdfService>()
    private val dokarkivClient = mockk<DokarkivClient>()
    private val dokarkivService = DokarkivService(dokarkivClient)
    private val journalforingService = JournalforingService(pdfService, dokarkivService, Sikkerlogg)
    private val saksbehandlingApiKlient = mockk<SaksbehandlingApiKlient>(relaxed = true)
    private val saksnummer = "1234"
    private val navn = getTestNavnFraPdl()
    private val journalpostId = JournalpostId("15")

    private fun withSetup(test: suspend (SøknadRepo, SøknadJobbService) -> Unit) {
        clearMocks(saksbehandlingApiKlient, pdlService, pdfService, dokarkivClient)
        coEvery { saksbehandlingApiKlient.hentEllerOpprettSaksnummer(any(), any()) } returns saksnummer.right()
        coEvery { pdlService.hentNavnForFnr(any(), any()) } returns navn.right()
        coEvery { pdfService.lagPdf(any()) } returns ("pdf".toByteArray() to null).right()
        coEvery { pdfService.konverterVedlegg(any()) } returns emptyList<Vedlegg>().right()
        coEvery { dokarkivClient.opprettJournalpost(any(), any()) } returns journalpostId.right()
        coEvery { saksbehandlingApiKlient.sendSøknad(any(), any()) } returns Unit.right()
        testDatabaseManager.withMigratedDb(runIsolated = true) { dataSource ->
            val søknadRepo = SøknadRepo(dataSource)
            val søknadJobbService = SøknadJobbService(søknadRepo, pdlService, journalforingService, saksbehandlingApiKlient, fixedClock, Sikkerlogg)
            runBlocking { test(søknadRepo, søknadJobbService) }
        }
    }

    @Test
    fun `hentEllerOpprettSaksnummer - saksnummer mangler, eier TP - henter og lagrer saksnummer`() = withSetup { søknadRepo, søknadJobbService ->
        val correlationId = CorrelationId.generate()
        val opprettet = nå(fixedClock)
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = opprettet,
            eier = Applikasjonseier.Tiltakspenger,
            saksnummer = null,
        )
        søknadRepo.lagre(mottattSøknad)

        søknadJobbService.hentEllerOpprettSaksnummer(correlationId)

        val oppdatertSoknad = søknadRepo.hentSoknad(mottattSøknad.id)
        oppdatertSoknad?.saksnummer shouldBe saksnummer
    }

    @Test
    fun `hentEllerOpprettSaksnummer - saksnummer mangler, eier Arena - oppdaterer ikke`() = withSetup { søknadRepo, søknadJobbService ->
        val correlationId = CorrelationId.generate()
        val opprettet = nå(fixedClock)
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = opprettet,
            eier = Applikasjonseier.Arena,
            saksnummer = null,
        )
        søknadRepo.lagre(mottattSøknad)

        søknadJobbService.hentEllerOpprettSaksnummer(correlationId)

        val soknadFraDb = søknadRepo.hentSoknad(mottattSøknad.id)
        soknadFraDb shouldNotBe null
        soknadFraDb?.saksnummer shouldBe null
    }

    @Test
    fun `journalførLagredeSøknader - eier TP - journalfører og ferdigstiller automatisk`() = withSetup { søknadRepo, søknadJobbService ->
        val correlationId = CorrelationId.generate()
        val opprettet = nå(fixedClock)
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = opprettet,
            eier = Applikasjonseier.Tiltakspenger,
            saksnummer = "232323",
            vedlegg = emptyList(),
        )
        søknadRepo.lagre(mottattSøknad)

        søknadJobbService.journalførLagredeSøknader(correlationId)

        val oppdatertSoknad = søknadRepo.hentSoknad(mottattSøknad.id)
        oppdatertSoknad?.fornavn shouldBe navn.fornavn
        oppdatertSoknad?.etternavn shouldBe navn.etternavn
        oppdatertSoknad?.journalpostId shouldBe journalpostId
        oppdatertSoknad?.journalført shouldNotBe null

        coVerify {
            dokarkivClient.opprettJournalpost(
                match { it.journalfoerendeEnhet == JOURNALFORENDE_ENHET_AUTOMATISK_BEHANDLING && it.sak?.fagsakId == mottattSøknad.saksnummer && it.kanFerdigstilleAutomatisk() },
                any(),
            )
        }
    }

    @Test
    fun `journalførLagredeSøknader - eier arena - oppretter journalpost, ferdigstiller ikke`() = withSetup { søknadRepo, søknadJobbService ->
        val correlationId = CorrelationId.generate()
        val opprettet = nå(fixedClock)
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = opprettet,
            eier = Applikasjonseier.Arena,
            saksnummer = null,
            vedlegg = emptyList(),
        )
        søknadRepo.lagre(mottattSøknad)

        søknadJobbService.journalførLagredeSøknader(correlationId)

        val oppdatertSoknad = søknadRepo.hentSoknad(mottattSøknad.id)
        oppdatertSoknad?.fornavn shouldBe navn.fornavn
        oppdatertSoknad?.etternavn shouldBe navn.etternavn
        oppdatertSoknad?.journalpostId shouldBe journalpostId
        oppdatertSoknad?.journalført shouldNotBe null

        coVerify {
            dokarkivClient.opprettJournalpost(
                match { it.journalfoerendeEnhet == null && it.sak == null && !it.kanFerdigstilleAutomatisk() },
                any(),
            )
        }
    }

    @Test
    fun `sendJournalførteSøknaderTilSaksbehandlingApi - eier TP - sender til saksbehandling-api`() = withSetup { søknadRepo, søknadJobbService ->
        val correlationId = CorrelationId.generate()
        val opprettet = nå(fixedClock)
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = opprettet,
            eier = Applikasjonseier.Tiltakspenger,
            saksnummer = "232323",
            vedlegg = emptyList(),
        ).copy(
            søknad = søknad(),
            journalpostId = journalpostId,
            journalført = opprettet,
        )
        søknadRepo.lagre(mottattSøknad)

        søknadJobbService.sendJournalførteSøknaderTilSaksbehandlingApi(correlationId)

        val oppdatertSoknad = søknadRepo.hentSoknad(mottattSøknad.id)
        oppdatertSoknad?.sendtTilVedtak shouldNotBe null

        coVerify {
            saksbehandlingApiKlient.sendSøknad(
                match { it.søknadId == mottattSøknad.søknad?.id && it.journalpostId == mottattSøknad.journalpostId?.toString() && it.saksnummer == mottattSøknad.saksnummer },
                correlationId,
            )
        }
    }

    @Test
    fun `hentEllerOpprettSaksnummer - kallet feiler - hopper over søknaden uten å kaste`() = withSetup { søknadRepo, søknadJobbService ->
        coEvery { saksbehandlingApiKlient.hentEllerOpprettSaksnummer(any(), any()) } returns
            HttpKlientError.UventetStatus(500, "saksbehandling-api er nede", tomMetadata()).left()
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = nå(fixedClock),
            eier = Applikasjonseier.Tiltakspenger,
            saksnummer = null,
        )
        søknadRepo.lagre(mottattSøknad)

        søknadJobbService.hentEllerOpprettSaksnummer(CorrelationId.generate())

        søknadRepo.hentSoknad(mottattSøknad.id)?.saksnummer shouldBe null
    }

    @Test
    fun `journalførLagredeSøknader - TP-søknad mangler saksnummer - kaster`() = withSetup { søknadRepo, søknadJobbService ->
        // Tomt saksnummer passerer repo-spørringens `saksnummer is not null`, men skal stoppes av guarden i jobben.
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = nå(fixedClock),
            eier = Applikasjonseier.Tiltakspenger,
            saksnummer = "",
            vedlegg = emptyList(),
        )
        søknadRepo.lagre(mottattSøknad)

        shouldThrow<IllegalStateException> {
            søknadJobbService.journalførLagredeSøknader(CorrelationId.generate())
        }
    }

    @Test
    fun `journalførLagredeSøknader - pdl-kallet feiler - hopper over søknaden uten å kaste`() = withSetup { søknadRepo, søknadJobbService ->
        coEvery { pdlService.hentNavnForFnr(any(), any()) } returns
            KanIkkeHentePerson.KallFeilet(HttpKlientError.UventetStatus(500, "pdl er nede", tomMetadata())).left()
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = nå(fixedClock),
            eier = Applikasjonseier.Tiltakspenger,
            saksnummer = "232323",
            vedlegg = emptyList(),
        )
        søknadRepo.lagre(mottattSøknad)

        søknadJobbService.journalførLagredeSøknader(CorrelationId.generate())

        søknadRepo.hentSoknad(mottattSøknad.id)?.journalført shouldBe null
    }

    @Test
    fun `journalførLagredeSøknader - journalføringen feiler - hopper over søknaden uten å kaste`() = withSetup { søknadRepo, søknadJobbService ->
        coEvery { dokarkivClient.opprettJournalpost(any(), any()) } returns
            KunneIkkeJournalføre.KallFeilet(HttpKlientError.UventetStatus(500, "dokarkiv er nede", tomMetadata())).left()
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = nå(fixedClock),
            eier = Applikasjonseier.Tiltakspenger,
            saksnummer = "232323",
            vedlegg = emptyList(),
        )
        søknadRepo.lagre(mottattSøknad)

        søknadJobbService.journalførLagredeSøknader(CorrelationId.generate())

        søknadRepo.hentSoknad(mottattSøknad.id)?.journalført shouldBe null
    }

    @Test
    fun `sendJournalførteSøknaderTilSaksbehandlingApi - sending feiler - hopper over søknaden uten å kaste`() = withSetup { søknadRepo, søknadJobbService ->
        coEvery { saksbehandlingApiKlient.sendSøknad(any(), any()) } returns
            HttpKlientError.UventetStatus(500, "saksbehandling-api er nede", tomMetadata()).left()
        val opprettet = nå(fixedClock)
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = opprettet,
            eier = Applikasjonseier.Tiltakspenger,
            saksnummer = "232323",
            vedlegg = emptyList(),
        ).copy(
            søknad = søknad(),
            journalpostId = journalpostId,
            journalført = opprettet,
        )
        søknadRepo.lagre(mottattSøknad)

        søknadJobbService.sendJournalførteSøknaderTilSaksbehandlingApi(CorrelationId.generate())

        søknadRepo.hentSoknad(mottattSøknad.id)?.sendtTilVedtak shouldBe null
    }

    @Test
    fun `sendJournalførteSøknaderTilSaksbehandlingApi - eier Arena - sender ikke til saksbehandling-api`() = withSetup { søknadRepo, søknadJobbService ->
        val correlationId = CorrelationId.generate()
        val opprettet = nå(fixedClock)
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = opprettet,
            eier = Applikasjonseier.Arena,
            saksnummer = null,
            vedlegg = emptyList(),
        ).copy(
            søknad = søknad(),
            journalpostId = journalpostId,
            journalført = opprettet,
        )
        søknadRepo.lagre(mottattSøknad)

        søknadJobbService.sendJournalførteSøknaderTilSaksbehandlingApi(correlationId)

        val soknadFraDb = søknadRepo.hentSoknad(mottattSøknad.id)
        soknadFraDb?.sendtTilVedtak shouldBe null

        coVerify(exactly = 0) {
            saksbehandlingApiKlient.sendSøknad(any(), any())
        }
    }
}
