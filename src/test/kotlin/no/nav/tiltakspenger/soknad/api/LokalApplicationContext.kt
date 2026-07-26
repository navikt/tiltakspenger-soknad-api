package no.nav.tiltakspenger.soknad.api

import io.prometheus.client.CollectorRegistry
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.texas.client.TexasClient
import no.nav.tiltakspenger.soknad.api.antivirus.ClamAvClient
import no.nav.tiltakspenger.soknad.api.db.DataSourceSetup
import no.nav.tiltakspenger.soknad.api.dokarkiv.DokarkivClient
import no.nav.tiltakspenger.soknad.api.pdf.PdfClient
import no.nav.tiltakspenger.soknad.api.pdl.client.PdlClient
import no.nav.tiltakspenger.soknad.api.saksbehandlingApi.SaksbehandlingApiKlient
import no.nav.tiltakspenger.soknad.api.soknad.SøknadPostgresRepo
import no.nav.tiltakspenger.soknad.api.testutils.LokalHttpTransport
import no.nav.tiltakspenger.soknad.api.testutils.TestTokenProvider
import no.nav.tiltakspenger.soknad.api.testutils.TexasClientFakeLokal
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakspengerTiltakClient
import java.net.URI
import java.time.Clock

/**
 * Konteksten [LokalMain] kjører med.
 * Det eneste som må kjøre ved siden av, er postgres fra docker-compose; alt utgående besvares av [LokalHttpTransport], og Texas godtar hvilket som helst token.
 *
 * Sett `BRUK_MOCK_API=true` for å gå mot compose-oppsettet i stedet (`tiltakspenger-soknad-mock-api`, pdfgen og authserveren), altså slik det var før fakene kom.
 * Klientene er de ekte i begge tilfeller — det er kun transporten som byttes, så hele klient-pipelinen kjører uansett.
 */
class LokalApplicationContext(
    clock: Clock,
    private val fnr: String = "12345678910",
) : ApplicationContext(
    clock = clock,
    søknadRepo = SøknadPostgresRepo(DataSourceSetup.createDatasource(Configuration.database().url)),
    collectorRegistry = CollectorRegistry.defaultRegistry,
) {
    private val brukMockApi: Boolean = System.getenv("BRUK_MOCK_API").toBoolean()

    private val transport: HttpTransport = LokalHttpTransport(
        clock = clock,
        fnr = fnr,
        avPath = URI.create(Configuration.avUrl).path,
    )

    override val texasClient: TexasClient =
        if (brukMockApi) super.texasClient else TexasClientFakeLokal(clock, fnr)

    override val pdlClient: PdlClient =
        if (brukMockApi) {
            super.pdlClient
        } else {
            PdlClient(
                endepunkt = Configuration.pdlUrl,
                clock = clock,
                pdlScope = Configuration.pdlScope,
                texasClient = texasClient,
                authTokenProvider = TestTokenProvider(),
                transport = transport,
            )
        }

    override val tiltakspengerTiltakClient: TiltakspengerTiltakClient =
        if (brukMockApi) {
            super.tiltakspengerTiltakClient
        } else {
            TiltakspengerTiltakClient(
                tiltakspengerTiltakEndpoint = Configuration.tiltakspengerTiltakUrl,
                clock = clock,
                tiltakspengerTiltakScope = Configuration.tiltakspengerTiltakScope,
                texasClient = texasClient,
                transport = transport,
            )
        }

    override val clamAvClient: ClamAvClient =
        if (brukMockApi) {
            super.clamAvClient
        } else {
            ClamAvClient(avEndpoint = Configuration.avUrl, clock = clock, transport = transport)
        }

    override val pdfClient: PdfClient =
        if (brukMockApi) {
            super.pdfClient
        } else {
            PdfClient(
                pdfEndpoint = Configuration.pdfUrl,
                pdfgenrsEndpoint = Configuration.pdfgenrsUrl,
                isLocalOrDev = Configuration.isLocalOrDev(),
                clock = clock,
                transport = transport,
            )
        }

    override val dokarkivClient: DokarkivClient =
        if (brukMockApi) {
            super.dokarkivClient
        } else {
            DokarkivClient(
                baseUrl = Configuration.dokarkivUrl,
                clock = clock,
                authTokenProvider = TestTokenProvider(),
                transport = transport,
            )
        }

    override val saksbehandlingApiKlient: SaksbehandlingApiKlient =
        if (brukMockApi) {
            super.saksbehandlingApiKlient
        } else {
            SaksbehandlingApiKlient(
                baseUrl = Configuration.saksbehandlingApiUrl,
                clock = clock,
                authTokenProvider = TestTokenProvider(),
                transport = transport,
            )
        }
}
