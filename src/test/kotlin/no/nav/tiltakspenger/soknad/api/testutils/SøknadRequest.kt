package no.nav.tiltakspenger.soknad.api.testutils

import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.tiltakspenger.soknad.api.SØKNAD_PATH
import no.nav.tiltakspenger.soknad.api.soknad.validering.spørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.soknad.validering.toJsonString

/**
 * Sender en ekte multipart-body inn på søknadsruta, slik at både multipart-parsingen og valideringen kjører.
 * [søknadJson] `null` utelater `søknad`-parten, og [ukjentFormItem] legger på en form-item med et annet navn.
 */
suspend fun ApplicationTestBuilder.postSøknad(
    token: String?,
    søknadJson: String? = spørsmålsbesvarelser().toJsonString(),
    vedlegg: ByteArray? = null,
    ukjentFormItem: Boolean = false,
): HttpResponse = client.post(SØKNAD_PATH) {
    token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    setBody(
        MultiPartFormDataContent(
            formData {
                søknadJson?.let {
                    append(
                        "søknad",
                        it,
                        Headers.build { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) },
                    )
                }
                if (ukjentFormItem) {
                    append("ukjent", "verdi")
                }
                vedlegg?.let {
                    append(
                        "vedlegg",
                        it,
                        Headers.build {
                            append(HttpHeaders.ContentType, "application/pdf")
                            append(HttpHeaders.ContentDisposition, """filename="vedlegg.pdf"""")
                        },
                    )
                }
            },
            "WebAppBoundary",
        ),
    )
}
