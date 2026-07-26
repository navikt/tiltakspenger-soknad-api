package no.nav.tiltakspenger.soknad.api

import io.prometheus.client.CollectorRegistry
import no.nav.tiltakspenger.libs.texas.client.TexasClient
import no.nav.tiltakspenger.soknad.api.antivirus.AvKlient
import no.nav.tiltakspenger.soknad.api.dokarkiv.JournalpostKlient
import no.nav.tiltakspenger.soknad.api.pdf.PdfGenerator
import no.nav.tiltakspenger.soknad.api.pdl.client.PersonKlient
import no.nav.tiltakspenger.soknad.api.saksbehandlingApi.SaksbehandlingKlient
import no.nav.tiltakspenger.soknad.api.soknad.SøknadRepo
import no.nav.tiltakspenger.soknad.api.testutils.AvKlientFake
import no.nav.tiltakspenger.soknad.api.testutils.JournalpostKlientFake
import no.nav.tiltakspenger.soknad.api.testutils.PdfGeneratorFake
import no.nav.tiltakspenger.soknad.api.testutils.PersonKlientFake
import no.nav.tiltakspenger.soknad.api.testutils.SaksbehandlingKlientFake
import no.nav.tiltakspenger.soknad.api.testutils.TexasClientFakeLokal
import no.nav.tiltakspenger.soknad.api.testutils.TiltakKlientFake
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakKlient
import java.time.Clock
import java.time.LocalDate

/**
 * Konteksten [LokalMain] kjører med.
 * Hver utgående avhengighet er byttet ut med sin egen fake, og Texas godtar hvilket som helst token, så postgres er det eneste som må kjøre ved siden av.
 *
 * Sett `BRUK_MOCK_API=true` for å gå mot compose-oppsettet i stedet (`tiltakspenger-soknad-mock-api`, pdfgen og authserveren), altså med de ekte klientene over nettverket.
 * Klientene selv dekkes av sine egne tester over `FakeHttpTransport`; her er poenget at appen kjører uten eksterne avhengigheter.
 *
 * Konteksten gjør ingen I/O ved konstruksjon — [søknadRepo] tas inn ferdig, akkurat som i [ApplicationContext] — slik at den kan bygges i en test.
 */
class LokalApplicationContext(
    clock: Clock,
    søknadRepo: SøknadRepo,
    private val fnr: String = "12345678910",
    /** Drift bruker det globale registeret; tester sender inn sitt eget, slik at to kontekster i samme JVM ikke kolliderer. */
    collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry,
) : ApplicationContext(
    clock = clock,
    søknadRepo = søknadRepo,
    collectorRegistry = collectorRegistry,
) {
    private val brukMockApi: Boolean = System.getenv("BRUK_MOCK_API").toBoolean()

    /** Barn under 16 år, slik at barnetillegg kan fylles ut lokalt. */
    private val barn: Map<String, LocalDate> = mapOf(
        "01011012345" to LocalDate.now(clock).minusYears(8),
        "02022014567" to LocalDate.now(clock).minusYears(12),
    )

    override val texasClient: TexasClient =
        if (brukMockApi) super.texasClient else TexasClientFakeLokal(clock, fnr)

    override val personKlient: PersonKlient =
        if (brukMockApi) super.personKlient else PersonKlientFake(clock = clock, standardBarn = barn)

    override val tiltakKlient: TiltakKlient =
        if (brukMockApi) super.tiltakKlient else TiltakKlientFake(clock)

    override val avKlient: AvKlient =
        if (brukMockApi) super.avKlient else AvKlientFake()

    override val pdfGenerator: PdfGenerator =
        if (brukMockApi) super.pdfGenerator else PdfGeneratorFake()

    override val journalpostKlient: JournalpostKlient =
        if (brukMockApi) super.journalpostKlient else JournalpostKlientFake()

    override val saksbehandlingKlient: SaksbehandlingKlient =
        if (brukMockApi) super.saksbehandlingKlient else SaksbehandlingKlientFake()
}
