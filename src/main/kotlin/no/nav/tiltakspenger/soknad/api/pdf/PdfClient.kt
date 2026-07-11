package no.nav.tiltakspenger.soknad.api.pdf

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import no.nav.tiltakspenger.libs.json.objectMapper
import no.nav.tiltakspenger.soknad.api.domain.Søknad
import no.nav.tiltakspenger.soknad.api.util.Bilde
import no.nav.tiltakspenger.soknad.api.util.Detect.APPLICATON_PDF
import no.nav.tiltakspenger.soknad.api.util.Detect.IMAGE_JPEG
import no.nav.tiltakspenger.soknad.api.util.Detect.IMAGE_PNG
import no.nav.tiltakspenger.soknad.api.util.Detect.detect
import no.nav.tiltakspenger.soknad.api.util.PdfTools
import no.nav.tiltakspenger.soknad.api.util.UnsupportedContentException
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import java.util.UUID
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
 */
class PdfClient(
    private val pdfEndpoint: String,
    private val pdfgenrsEndpoint: String,
    private val isLocalOrDev: Boolean,
    private val client: HttpClient,
) : PdfGenerator {
    private val log = KotlinLogging.logger {}

    /*
        TODO - pdfgenrs: skift tilbake til ByteArray når det er verifisert at PDF fra pdfgenrs er ok.
            I local/dev kalles pdfgenrs i parallell (skygge-kall) slik at begge PDF-ene kan
            journalføres og sammenlignes manuelt i Gosys.
     */
    override suspend fun genererPdf(søknad: Søknad): Pair<ByteArray, ByteArray?> {
        log.info { "Starter generering av søknadspdf for søknadId ${søknad.id}" }
        return if (isLocalOrDev) {
            coroutineScope {
                val pdfgenDeferred = async {
                    measureTimedValue { genererPdf(søknad, pdfEndpoint) }
                }
                val pdfgenrsDeferred = async {
                    measureTimedValue { genererPdf(søknad, pdfgenrsEndpoint) }
                }

                val (pdfgen, pdfgenDuration) = pdfgenDeferred.await()
                val (pdfgenrs, pdfgenrsDuration) = pdfgenrsDeferred.await()

                log.info { "pdfgen brukte $pdfgenDuration, pdfgenrs brukte $pdfgenrsDuration" }

                Pair(pdfgen, pdfgenrs)
            }
        } else {
            Pair(genererPdf(søknad, pdfEndpoint), null)
        }
    }

    private suspend fun genererPdf(søknad: Søknad, endpoint: String): ByteArray {
        try {
            return client.post("$endpoint/$PDFGEN_PATH/$SOKNAD_TEMPLATE") {
                accept(ContentType.Application.Json)
                header("X-Correlation-ID", UUID.randomUUID())
                contentType(ContentType.Application.Json)
                setBody(objectMapper.writeValueAsString(søknad))
            }.body()
        } catch (throwable: Throwable) {
            throw RuntimeException("PdfClient: Feilet å lage PDF for søknad ${søknad.id} mot $endpoint", throwable)
        }
    }

    override suspend fun konverterVedlegg(vedlegg: List<Vedlegg>): List<Vedlegg> {
        return vedlegg.map {
            log.info { "Starter konvertering av vedlegg}" }
            val contentType = it.dokument.detect()
            when (contentType) {
                APPLICATON_PDF -> {
                    log.info { "Oppdaget PDF-vedlegg, konverterer til bilde" }
                    val bilder = PdfTools.konverterPdfTilBilder(it.dokument)
                    log.info { "Konverterer bilder tilbake til PDF" }
                    val enkeltsider = bilder.map { bilde ->
                        genererPdfFraBilde(Bilde(ContentType.Image.PNG, bilde.data))
                    }
                    val resultatPdf = PdfTools.slåSammenPdfer(enkeltsider)
                    Vedlegg(it.filnavn, "application/pdf", resultatPdf)
                }

                IMAGE_PNG -> {
                    log.info { "Oppdaget PNG-vedlegg, konverterer til PDF" }
                    val pdfFraBilde = genererPdfFraBilde(Bilde(ContentType.Image.PNG, it.dokument))
                    Vedlegg("$${it.filnavn}-konvertert.pdf", "application/pdf", pdfFraBilde)
                }

                IMAGE_JPEG -> {
                    log.info { "Oppdaget JPEG-vedlegg, konverterer til PDF" }
                    val pdfFraBilde = genererPdfFraBilde(Bilde(ContentType.Image.JPEG, it.dokument))
                    Vedlegg("$${it.filnavn}-konvertert.pdf", "application/pdf", pdfFraBilde)
                }

                else -> {
                    throw UnsupportedContentException("Ugyldig filformat")
                }
            }
        }
    }

    private suspend fun genererPdfFraBilde(bilde: Bilde): ByteArray {
        try {
            return client.post("$pdfEndpoint/$PDFGEN_IMAGE_PATH") {
                accept(ContentType.Application.Json)
                header("X-Correlation-ID", UUID.randomUUID())
                contentType(bilde.type)
                setBody(ByteArrayContent(bilde.data))
            }.body()
        } catch (throwable: Throwable) {
            throw RuntimeException(
                "PdfClient: Feilet å generere PDF fra bilde med Content-Type ${bilde.type}",
                throwable,
            )
        }
    }
}
