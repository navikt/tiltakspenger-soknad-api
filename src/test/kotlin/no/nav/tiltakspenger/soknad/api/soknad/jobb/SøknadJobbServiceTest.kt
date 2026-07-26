package no.nav.tiltakspenger.soknad.api.soknad.jobb

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.soknad.api.dokarkiv.JOURNALFORENDE_ENHET_AUTOMATISK_BEHANDLING
import no.nav.tiltakspenger.soknad.api.soknad.Applikasjonseier
import no.nav.tiltakspenger.soknad.api.soknad.MottattSøknad
import no.nav.tiltakspenger.soknad.api.soknad.validering.søknad
import no.nav.tiltakspenger.soknad.api.testutils.TestApplicationContext
import no.nav.tiltakspenger.soknad.api.testutils.enkelPdf
import no.nav.tiltakspenger.soknad.api.testutils.leggIKøStatusForAlleForsøk
import no.nav.tiltakspenger.soknad.api.testutils.søkerRespons
import no.nav.tiltakspenger.soknad.api.util.genererMottattSøknadForTest
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import org.junit.jupiter.api.Test

/**
 * Ende-til-ende for bakgrunnsjobbene: fra det som ligger i repoet, gjennom servicene og de ekte klientene, og ut på transporten.
 * Jobbene har ingen route, så dette er inngangen som tilsvarer en rute-test.
 */
internal class SøknadJobbServiceTest {
    private val correlationId = CorrelationId.generate()
    private val saksnummer = "232323"

    private fun TestApplicationContext.medSøknad(søknad: MottattSøknad): MottattSøknad {
        søknadRepo.lagre(søknad)
        return søknad
    }

    private fun tpSøknad(
        saksnummer: String? = null,
        vedlegg: List<Vedlegg> = emptyList(),
    ) = genererMottattSøknadForTest(
        opprettet = nå(fixedClock),
        eier = Applikasjonseier.Tiltakspenger,
        saksnummer = saksnummer,
        vedlegg = vedlegg,
    )

    private fun arenaSøknad(vedlegg: List<Vedlegg> = emptyList()) = genererMottattSøknadForTest(
        opprettet = nå(fixedClock),
        eier = Applikasjonseier.Arena,
        saksnummer = null,
        vedlegg = vedlegg,
    )

    /** Køer svarene journalføringen trenger: navn fra PDL, søknads-PDF og journalpost. */
    private fun TestApplicationContext.køJournalføringOk(journalpostferdigstilt: Boolean = true) {
        pdlTransport.leggIKøJson(søkerRespons(fornavn = "Fornavn", mellomnavn = "Mellomnavn", etternavn = "Etternavn"))
        pdfTransport.leggIKøBytes(enkelPdf(), contentType = "application/pdf")
        dokarkivTransport.leggIKøJson("""{"journalpostId":"15","journalpostferdigstilt":$journalpostferdigstilt}""")
    }

    @Test
    fun `hentEllerOpprettSaksnummer henter og lagrer saksnummer for TP-søknad`() = runTest {
        val tac = TestApplicationContext()
        val søknad = tac.medSøknad(tpSøknad(saksnummer = null))
        tac.saksbehandlingApiTransport.leggIKøJson("""{"saksnummer":"$saksnummer"}""")

        tac.søknadJobbService.hentEllerOpprettSaksnummer(correlationId)

        tac.søknadRepo.hentSoknad(søknad.id)?.saksnummer shouldBe saksnummer
        tac.saksbehandlingApiTransport.mottatteKall.single().uri.toString() shouldBe "http://saksbehandling.test/saksnummer"
    }

    @Test
    fun `hentEllerOpprettSaksnummer rører ikke arena-søknader`() = runTest {
        val tac = TestApplicationContext()
        val søknad = tac.medSøknad(arenaSøknad())

        tac.søknadJobbService.hentEllerOpprettSaksnummer(correlationId)

        tac.søknadRepo.hentSoknad(søknad.id)?.saksnummer shouldBe null
        tac.saksbehandlingApiTransport.mottatteKall shouldBe emptyList()
    }

    @Test
    fun `hentEllerOpprettSaksnummer hopper over søknaden når kallet feiler`() = runTest {
        val tac = TestApplicationContext()
        val søknad = tac.medSøknad(tpSøknad(saksnummer = null))
        tac.saksbehandlingApiTransport.leggIKøStatusForAlleForsøk(500, "saksbehandling-api er nede")

        tac.søknadJobbService.hentEllerOpprettSaksnummer(correlationId)

        tac.søknadRepo.hentSoknad(søknad.id)?.saksnummer shouldBe null
    }

    @Test
    fun `journalførLagredeSøknader journalfører og ferdigstiller TP-søknad automatisk`() = runTest {
        val tac = TestApplicationContext()
        val søknad = tac.medSøknad(tpSøknad(saksnummer = saksnummer))
        tac.køJournalføringOk()

        tac.søknadJobbService.journalførLagredeSøknader(correlationId)

        val oppdatert = tac.søknadRepo.hentSoknad(søknad.id)!!
        oppdatert.fornavn shouldBe "Fornavn"
        oppdatert.etternavn shouldBe "Etternavn"
        oppdatert.journalpostId shouldBe JournalpostId("15")
        oppdatert.journalført shouldNotBe null

        val journalpostKall = tac.dokarkivTransport.mottatteKall.single()
        journalpostKall.uri.toString() shouldContain "forsoekFerdigstill=true"
        journalpostKall.bodyTekst shouldContain JOURNALFORENDE_ENHET_AUTOMATISK_BEHANDLING
        journalpostKall.bodyTekst shouldContain saksnummer
    }

    @Test
    fun `journalførLagredeSøknader oppretter journalpost for arena-søknad uten å ferdigstille`() = runTest {
        val tac = TestApplicationContext()
        val søknad = tac.medSøknad(arenaSøknad())
        tac.køJournalføringOk(journalpostferdigstilt = false)

        tac.søknadJobbService.journalførLagredeSøknader(correlationId)

        tac.søknadRepo.hentSoknad(søknad.id)?.journalpostId shouldBe JournalpostId("15")

        val journalpostKall = tac.dokarkivTransport.mottatteKall.single()
        journalpostKall.uri.toString() shouldContain "forsoekFerdigstill=false"
    }

    @Test
    fun `journalførLagredeSøknader konverterer vedlegg til pdf før journalføring`() = runTest {
        val tac = TestApplicationContext()
        val vedlegg = Vedlegg(filnavn = "vedlegg.pdf", contentType = "application/pdf", dokument = enkelPdf())
        val søknad = tac.medSøknad(tpSøknad(saksnummer = saksnummer, vedlegg = listOf(vedlegg)))
        tac.pdlTransport.leggIKøJson(søkerRespons())
        // Først søknads-PDF-en, så én PDF per side i vedlegget.
        tac.pdfTransport.leggIKøBytes(enkelPdf(), contentType = "application/pdf")
        tac.pdfTransport.leggIKøBytes(enkelPdf(), contentType = "application/pdf")
        tac.dokarkivTransport.leggIKøJson("""{"journalpostId":"15","journalpostferdigstilt":true}""")

        tac.søknadJobbService.journalførLagredeSøknader(correlationId)

        tac.søknadRepo.hentSoknad(søknad.id)?.journalført shouldNotBe null
        tac.pdfTransport.mottatteKall.map { it.uri.path } shouldBe listOf(
            "/api/v1/genpdf/tpts/soknad",
            "/api/v1/genpdf/image/tpts",
        )
    }

    @Test
    fun `journalførLagredeSøknader journalfører også skygge-pdf-en fra pdfgenrs lokalt og i dev`() = runTest {
        val tac = TestApplicationContext(isLocalOrDev = true)
        val søknad = tac.medSøknad(tpSøknad(saksnummer = saksnummer))
        tac.pdlTransport.leggIKøJson(søkerRespons())
        // pdfgen og pdfgenrs kalles i parallell, så begge svarene må ligge i køen.
        tac.pdfTransport.leggIKøBytes(enkelPdf(), contentType = "application/pdf")
        tac.pdfTransport.leggIKøBytes(enkelPdf(), contentType = "application/pdf")
        tac.dokarkivTransport.leggIKøJson("""{"journalpostId":"15","journalpostferdigstilt":true}""")
        tac.dokarkivTransport.leggIKøJson("""{"journalpostId":"16","journalpostferdigstilt":true}""")

        tac.søknadJobbService.journalførLagredeSøknader(correlationId)

        tac.søknadRepo.hentSoknad(søknad.id)?.journalpostId shouldBe JournalpostId("15")
        tac.dokarkivTransport.mottatteKall.size shouldBe 2
    }

    @Test
    fun `journalførLagredeSøknader kaster når en TP-søknad mangler saksnummer`() = runTest {
        val tac = TestApplicationContext()
        // Tomt saksnummer passerer repo-spørringens `saksnummer is not null`, men skal stoppes av guarden i jobben.
        tac.medSøknad(tpSøknad(saksnummer = ""))

        shouldThrow<IllegalStateException> {
            tac.søknadJobbService.journalførLagredeSøknader(correlationId)
        }
    }

    @Test
    fun `journalførLagredeSøknader hopper over søknaden når pdl-kallet feiler`() = runTest {
        val tac = TestApplicationContext()
        val søknad = tac.medSøknad(tpSøknad(saksnummer = saksnummer))
        tac.pdlTransport.leggIKøStatus(500, "pdl er nede")

        tac.søknadJobbService.journalførLagredeSøknader(correlationId)

        tac.søknadRepo.hentSoknad(søknad.id)?.journalført shouldBe null
    }

    @Test
    fun `journalførLagredeSøknader hopper over søknaden når pdf-genereringen feiler`() = runTest {
        val tac = TestApplicationContext()
        val søknad = tac.medSøknad(tpSøknad(saksnummer = saksnummer))
        tac.pdlTransport.leggIKøJson(søkerRespons())
        tac.pdfTransport.leggIKøStatus(500, "pdfgen er nede")

        tac.søknadJobbService.journalførLagredeSøknader(correlationId)

        tac.søknadRepo.hentSoknad(søknad.id)?.journalført shouldBe null
        tac.dokarkivTransport.mottatteKall shouldBe emptyList()
    }

    @Test
    fun `journalførLagredeSøknader hopper over søknaden når vedleggskonverteringen feiler`() = runTest {
        val tac = TestApplicationContext()
        val vedlegg = Vedlegg(filnavn = "vedlegg.pdf", contentType = "application/pdf", dokument = enkelPdf())
        val søknad = tac.medSøknad(tpSøknad(saksnummer = saksnummer, vedlegg = listOf(vedlegg)))
        tac.pdlTransport.leggIKøJson(søkerRespons())
        tac.pdfTransport.leggIKøBytes(enkelPdf(), contentType = "application/pdf")
        tac.pdfTransport.leggIKøStatus(500, "pdfgen er nede")

        tac.søknadJobbService.journalførLagredeSøknader(correlationId)

        tac.søknadRepo.hentSoknad(søknad.id)?.journalført shouldBe null
    }

    @Test
    fun `journalførLagredeSøknader hopper over søknaden når vedlegget har et filformat vi ikke kan konvertere`() = runTest {
        val tac = TestApplicationContext()
        val vedlegg = Vedlegg(filnavn = "vedlegg.txt", contentType = "text/plain", dokument = "ikke en pdf".toByteArray())
        val søknad = tac.medSøknad(tpSøknad(saksnummer = saksnummer, vedlegg = listOf(vedlegg)))
        tac.pdlTransport.leggIKøJson(søkerRespons())
        tac.pdfTransport.leggIKøBytes(enkelPdf(), contentType = "application/pdf")

        tac.søknadJobbService.journalførLagredeSøknader(correlationId)

        tac.søknadRepo.hentSoknad(søknad.id)?.journalført shouldBe null
        tac.dokarkivTransport.mottatteKall shouldBe emptyList()
    }

    @Test
    fun `journalførLagredeSøknader hopper over søknaden når journalføringen feiler`() = runTest {
        val tac = TestApplicationContext()
        val søknad = tac.medSøknad(tpSøknad(saksnummer = saksnummer))
        tac.pdlTransport.leggIKøJson(søkerRespons())
        tac.pdfTransport.leggIKøBytes(enkelPdf(), contentType = "application/pdf")
        tac.dokarkivTransport.leggIKøStatusForAlleForsøk(500, "dokarkiv er nede")

        tac.søknadJobbService.journalførLagredeSøknader(correlationId)

        tac.søknadRepo.hentSoknad(søknad.id)?.journalført shouldBe null
    }

    @Test
    fun `journalførLagredeSøknader hopper over søknaden når journalposten ikke ble ferdigstilt`() = runTest {
        val tac = TestApplicationContext()
        val søknad = tac.medSøknad(tpSøknad(saksnummer = saksnummer))
        tac.køJournalføringOk(journalpostferdigstilt = false)

        tac.søknadJobbService.journalførLagredeSøknader(correlationId)

        tac.søknadRepo.hentSoknad(søknad.id)?.journalført shouldBe null
    }

    @Test
    fun `sendJournalførteSøknaderTilSaksbehandlingApi sender TP-søknad videre`() = runTest {
        val tac = TestApplicationContext()
        val søknad = tac.medSøknad(journalførtTpSøknad())
        tac.saksbehandlingApiTransport.leggIKøTomRespons(statusCode = 200)

        tac.søknadJobbService.sendJournalførteSøknaderTilSaksbehandlingApi(correlationId)

        tac.søknadRepo.hentSoknad(søknad.id)?.sendtTilVedtak shouldNotBe null
        val kall = tac.saksbehandlingApiTransport.mottatteKall.single()
        kall.uri.toString() shouldBe "http://saksbehandling.test/soknad"
        kall.bodyTekst shouldContain saksnummer
        kall.bodyTekst shouldContain "15"
    }

    @Test
    fun `sendJournalførteSøknaderTilSaksbehandlingApi hopper over søknaden når sendingen feiler`() = runTest {
        val tac = TestApplicationContext()
        val søknad = tac.medSøknad(journalførtTpSøknad())
        tac.saksbehandlingApiTransport.leggIKøStatusForAlleForsøk(500, "saksbehandling-api er nede")

        tac.søknadJobbService.sendJournalførteSøknaderTilSaksbehandlingApi(correlationId)

        tac.søknadRepo.hentSoknad(søknad.id)?.sendtTilVedtak shouldBe null
    }

    @Test
    fun `sendJournalførteSøknaderTilSaksbehandlingApi sender ikke arena-søknader`() = runTest {
        val tac = TestApplicationContext()
        val opprettet = nå(fixedClock)
        val søknad = tac.medSøknad(
            arenaSøknad().copy(
                søknad = søknad(),
                journalpostId = JournalpostId("15"),
                journalført = opprettet,
            ),
        )

        tac.søknadJobbService.sendJournalførteSøknaderTilSaksbehandlingApi(correlationId)

        tac.søknadRepo.hentSoknad(søknad.id)?.sendtTilVedtak shouldBe null
        tac.saksbehandlingApiTransport.mottatteKall shouldBe emptyList()
    }

    private fun journalførtTpSøknad(): MottattSøknad {
        val opprettet = nå(fixedClock)
        return tpSøknad(saksnummer = saksnummer).copy(
            søknad = søknad(),
            journalpostId = JournalpostId("15"),
            journalført = opprettet,
        )
    }
}
