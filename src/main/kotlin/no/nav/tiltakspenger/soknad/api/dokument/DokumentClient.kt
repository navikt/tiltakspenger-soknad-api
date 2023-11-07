package no.nav.tiltakspenger.soknad.api.dokument

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.server.config.ApplicationConfig
import mu.KotlinLogging
import no.nav.tiltakspenger.soknad.api.domain.SøknadDTO
import no.nav.tiltakspenger.soknad.api.objectMapper
import no.nav.tiltakspenger.soknad.api.soknad.SøknadResponse
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

internal const val SOKNAD_ROUTE_DOKUMENT = "soknad"
class DokumentClient(
    config: ApplicationConfig,
    private val client: HttpClient,
) {
    private val log = KotlinLogging.logger { }
    private val dokumentEndpoint = config.property("endpoints.dokument").getString()
    suspend fun sendSøknadTilDokument(søknadDTO: SøknadDTO, vedlegg: List<Vedlegg>): SøknadResponse {
        try {
            return client.submitFormWithBinaryData(
                url = "$dokumentEndpoint/$SOKNAD_ROUTE_DOKUMENT",
                formData = formData {
                    append("soknad", objectMapper.writeValueAsString(søknadDTO))
                    vedlegg.forEachIndexed { index, vedlegg ->
                        append(
                            "file$index",
                            vedlegg.dokument,
                            Headers.build {
                                append(HttpHeaders.ContentType, vedlegg.contentType)
                                append(HttpHeaders.ContentDisposition, "filename=${vedlegg.filnavn}")
                            },
                        )
                    }
                },
            ).body()
        } catch (throwable: Throwable) {
            log.error("Kallet til dokument feilet $throwable")
            throw RuntimeException("Kallet til dokument feilet $throwable")
        }
    }
}
