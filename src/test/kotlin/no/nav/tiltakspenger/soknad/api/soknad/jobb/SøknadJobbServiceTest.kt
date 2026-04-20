package no.nav.tiltakspenger.soknad.api.soknad.jobb

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.soknad.api.db.testDatabaseManager
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivClient
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivService
import no.nav.tiltakspenger.soknad.api.dokarkiv.JOURNALFORENDE_ENHET_AUTOMATISK_BEHANDLING
import no.nav.tiltakspenger.soknad.api.pdf.PdfService
import no.nav.tiltakspenger.soknad.api.pdl.PdlService
import no.nav.tiltakspenger.soknad.api.saksbehandlingApi.SaksbehandlingApiKlient
import no.nav.tiltakspenger.soknad.api.soknad.Applikasjonseier
import no.nav.tiltakspenger.soknad.api.soknad.SøknadRepo
import no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforing.JournalforingService
import no.nav.tiltakspenger.soknad.api.soknad.validering.søknad
import no.nav.tiltakspenger.soknad.api.util.genererMottattSøknadForTest
import no.nav.tiltakspenger.soknad.api.util.getTestNavnFraPdl
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SøknadJobbServiceTest {
    private val pdlService = mockk<PdlService>()
    private val pdfService = mockk<PdfService>()
    private val dokarkivClient = mockk<DokarkivClient>()
    private val dokarkivService = DokarkivService(dokarkivClient)
    private val journalforingService = JournalforingService(pdfService, dokarkivService)
    private val saksbehandlingApiKlient = mockk<SaksbehandlingApiKlient>(relaxed = true)
    private val saksnummer = "1234"
    private val navn = getTestNavnFraPdl()
    private val journalpostId = JournalpostId("15")

    private fun withSetup(test: suspend (SøknadRepo, SøknadJobbService) -> Unit) {
        clearMocks(saksbehandlingApiKlient, pdlService, pdfService, dokarkivClient)
        coEvery { saksbehandlingApiKlient.hentEllerOpprettSaksnummer(any(), any()) } returns saksnummer
        coEvery { pdlService.hentNavnForFnr(any(), any()) } returns navn
        coEvery { pdfService.lagPdf(any()) } returns "pdf".toByteArray()
        coEvery { pdfService.konverterVedlegg(any()) } returns emptyList()
        coEvery { dokarkivClient.opprettJournalpost(any(), any(), any()) } returns journalpostId
        testDatabaseManager.withMigratedDb(runIsolated = true) { dataSource ->
            val søknadRepo = SøknadRepo(dataSource)
            val søknadJobbService = SøknadJobbService(søknadRepo, pdlService, journalforingService, saksbehandlingApiKlient)
            runBlocking { test(søknadRepo, søknadJobbService) }
        }
    }

    @Test
    fun `hentEllerOpprettSaksnummer - saksnummer mangler, eier TP - henter og lagrer saksnummer`() = withSetup { søknadRepo, søknadJobbService ->
        val correlationId = CorrelationId.generate()
        val opprettet = LocalDateTime.now()
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
        val opprettet = LocalDateTime.now()
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
        val opprettet = LocalDateTime.now()
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
                mottattSøknad.id,
                any(),
            )
        }
    }

    @Test
    fun `journalførLagredeSøknader - eier arena - oppretter journalpost, ferdigstiller ikke`() = withSetup { søknadRepo, søknadJobbService ->
        val correlationId = CorrelationId.generate()
        val opprettet = LocalDateTime.now()
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
                mottattSøknad.id,
                any(),
            )
        }
    }

    @Test
    fun `sendJournalførteSøknaderTilSaksbehandlingApi - eier TP - sender til saksbehandling-api`() = withSetup { søknadRepo, søknadJobbService ->
        val correlationId = CorrelationId.generate()
        val opprettet = LocalDateTime.now()
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
    fun `sendJournalførteSøknaderTilSaksbehandlingApi - eier Arena - sender ikke til saksbehandling-api`() = withSetup { søknadRepo, søknadJobbService ->
        val correlationId = CorrelationId.generate()
        val opprettet = LocalDateTime.now()
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
