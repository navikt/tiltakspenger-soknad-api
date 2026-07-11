package no.nav.tiltakspenger.soknad.api.antivirus

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

/**
 * Klient for å virusskanne vedlegg med ClamAV.
 *
 * Kildekode: ikke aktuelt — ClamAV tilbys som plattformtjeneste av Nais
 * Dokumentasjon: https://doc.nais.io/services/antivirus/
 * API-spec: - (ingen spec; REST-API-et er beskrevet i nais-dokumentasjonen)
 * Slack: #nais
 * Teamkatalog: ikke aktuelt (plattformtjeneste)
 */
class ClamAvClient(
    private val avEndpoint: String,
    private val client: HttpClient,
) {
    suspend fun scan(vedleggsListe: List<Vedlegg>): List<AvSjekkResultat> {
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
