package no.nav.tiltakspenger.soknad.api.antivirus

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.server.config.ApplicationConfig
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

class AvClient(
    config: ApplicationConfig,
    private val client: HttpClient,
) : AntiVirus {
    private val avEndpoint = config.property("endpoints.av").getString()
    override suspend fun scan(vedleggsListe: List<Vedlegg>): List<AvSjekkResultat> {
        try {
            return client.submitFormWithBinaryData(
                url = avEndpoint,
                formData = formData {
                    vedleggsListe.forEachIndexed { index, vedlegg ->
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
            throw RuntimeException(
                "Kallet til antivirusinstans feilet. Message: ${throwable.message}",
                throwable,
            )
        }
    }
}

data class AvSjekkResultat(
    @JsonProperty("Filename") val filnavn: String,
    @JsonProperty("Result") val resultat: Status,
)

enum class Status {
    FOUND,
    OK,
    ERROR,
}
