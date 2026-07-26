package no.nav.tiltakspenger.soknad.api.pdf

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.kall.NavHeadere
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import no.nav.tiltakspenger.soknad.api.domain.Søknad
import no.nav.tiltakspenger.soknad.api.util.Bilde
import no.nav.tiltakspenger.soknad.api.util.Detect.APPLICATON_PDF
import no.nav.tiltakspenger.soknad.api.util.Detect.IMAGE_JPEG
import no.nav.tiltakspenger.soknad.api.util.Detect.IMAGE_PNG
import no.nav.tiltakspenger.soknad.api.util.Detect.detect
import no.nav.tiltakspenger.soknad.api.util.PdfTools
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import java.net.URI
import java.time.Clock
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

internal const val PDFGEN_PATH = "api/v1/genpdf/tpts"
internal const val PDFGEN_IMAGE_PATH = "api/v1/genpdf/image/tpts"
internal const val SOKNAD_TEMPLATE = "soknad"

/**
 * Klient for å generere søknads-PDF-er via tiltakspenger-pdfgen, med skygge-kall til tiltakspenger-pdfgenrs i local/dev.
 *
 * Kildekode: https://github.com/navikt/tiltakspenger-pdfgen og https://github.com/navikt/tiltakspenger-pdfgenrs
 * Dokumentasjon: README-ene i kildekode-repoene
 * API-spec: -
 * Slack: #tiltakspenger-værsågod (eget team)
 * Teamkatalog: https://teamkatalogen.nav.no/team/15bca3d2-2584-4167-85ba-faab1f1cfb53
 *
 * pdfgen er en intern tjeneste uten autentisering, derfor [KlientAuth.Ingen].
 * Ingen retry, som før migreringen — PDF-generering er dyr, og kalleren (journalføringsjobben) prøver uansett hele søknaden på nytt.
 *
 * Klienten logger ikke selv; feillogging skjer én gang i [no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforing.JournalforingService].
 * Unntaket er en midlertidig info-linje i [genererPdf] som sammenligner responstiden til pdfgen og pdfgenrs; den fjernes sammen med pdfgenrs-verifiseringen.
 *
 * @param transport Det eneste stedet klienten rører nettverket; default er produksjonstransporten, tester sender inn `FakeHttpTransport`.
 */
class PdfClient(
    pdfEndpoint: String,
    pdfgenrsEndpoint: String,
    private val isLocalOrDev: Boolean,
    clock: Clock,
    connectTimeout: Duration = 30.seconds,
    timeout: Duration = 30.seconds,
    transport: HttpTransport = JavaHttpTransport(connectTimeout = connectTimeout),
) : PdfGenerator {
    private val log = KotlinLogging.logger {}

    private val httpKlient: HttpKlient = HttpKlient(
        clock = clock,
        config = HttpKlientConfig(
            timeout = timeout,
            auth = KlientAuth.Ingen,
        ),
        transport = transport,
    )

    private val søknadUri = URI.create("$pdfEndpoint/$PDFGEN_PATH/$SOKNAD_TEMPLATE")
    private val pdfgenrsSøknadUri = URI.create("$pdfgenrsEndpoint/$PDFGEN_PATH/$SOKNAD_TEMPLATE")
    private val bildeUri = URI.create("$pdfEndpoint/$PDFGEN_IMAGE_PATH")

    /*
        TODO - pdfgenrs: skift tilbake til ByteArray når det er verifisert at PDF fra pdfgenrs er ok.
            I local/dev kalles pdfgenrs i parallell (skygge-kall) slik at begge PDF-ene kan journalføres og sammenlignes manuelt i Gosys.
     */
    override suspend fun genererPdf(søknad: Søknad): Either<HttpKlientError, Pair<ByteArray, ByteArray?>> {
        log.info { "Starter generering av søknadspdf for søknadId ${søknad.id}" }
        if (!isLocalOrDev) {
            return genererPdf(søknad, søknadUri).map { it to null }
        }
        return coroutineScope {
            val pdfgenDeferred = async { measureTimedValue { genererPdf(søknad, søknadUri) } }
            val pdfgenrsDeferred = async { measureTimedValue { genererPdf(søknad, pdfgenrsSøknadUri) } }

            val (pdfgen, pdfgenDuration) = pdfgenDeferred.await()
            val (pdfgenrs, pdfgenrsDuration) = pdfgenrsDeferred.await()

            log.info { "pdfgen brukte $pdfgenDuration, pdfgenrs brukte $pdfgenrsDuration" }

            pdfgen.flatMap { pdf -> pdfgenrs.map { skyggePdf -> pdf to skyggePdf } }
        }
    }

    private suspend fun genererPdf(søknad: Søknad, uri: URI): Either<HttpKlientError, ByteArray> =
        httpKlient.postJsonMotPdf(
            uri = uri,
            body = søknad,
            headere = listOf(NavHeadere.xCorrelationId(UUID.randomUUID().toString())),
        ).map { it.body }

    override suspend fun konverterVedlegg(vedlegg: List<Vedlegg>): Either<KunneIkkeKonvertereVedlegg, List<Vedlegg>> = either {
        vedlegg.map { konverter(it).bind() }
    }

    private suspend fun konverter(vedlegg: Vedlegg): Either<KunneIkkeKonvertereVedlegg, Vedlegg> = either {
        when (val contentType = vedlegg.dokument.detect()) {
            APPLICATON_PDF -> {
                // PDF-er går veien om bilder for å flate ut skjemafelter og annet som Gosys ikke viser.
                val enkeltsider = PdfTools.konverterPdfTilBilder(vedlegg.dokument).map { bilde ->
                    genererPdfFraBilde(bilde).mapLeft { KunneIkkeKonvertereVedlegg.KallFeilet(it) }.bind()
                }
                // Nel.map bevarer ikke-tomheten, så sammenslåingen får den garantien den trenger.
                Vedlegg(vedlegg.filnavn, APPLICATON_PDF, PdfTools.slåSammenPdfer(enkeltsider))
            }

            IMAGE_PNG, IMAGE_JPEG -> {
                val pdfFraBilde = genererPdfFraBilde(Bilde(contentType, vedlegg.dokument))
                    .mapLeft { KunneIkkeKonvertereVedlegg.KallFeilet(it) }
                    .bind()
                Vedlegg("$${vedlegg.filnavn}-konvertert.pdf", APPLICATON_PDF, pdfFraBilde)
            }

            else -> raise(KunneIkkeKonvertereVedlegg.UgyldigFilformat(contentType))
        }
    }

    private suspend fun genererPdfFraBilde(bilde: Bilde): Either<HttpKlientError, ByteArray> =
        httpKlient.postBytesMotPdf(
            uri = bildeUri,
            bytes = bilde.data,
            contentType = bilde.type,
            headere = listOf(NavHeadere.xCorrelationId(UUID.randomUUID().toString())),
        ).map { it.body }
}
