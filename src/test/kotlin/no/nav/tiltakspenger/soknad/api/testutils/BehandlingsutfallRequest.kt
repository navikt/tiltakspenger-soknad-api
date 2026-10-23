package no.nav.tiltakspenger.soknad.api.testutils

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.soknad.api.SAKSBEHANDLING_PATH

/** JSON-en saksbehandling-api sender når et søknadsvedtak er iverksatt. */
fun behandlingsutfallJson(
    søknadId: SøknadId,
    utfall: String = "INNVILGELSE",
    vedtakId: String = "vedtak_01J0000000000000000000000",
    vedtakstidspunkt: String = "2025-02-01T12:00:00",
): String =
    """{"søknadId":"$søknadId","vedtakId":"$vedtakId","utfall":"$utfall","vedtakstidspunkt":"$vedtakstidspunkt"}"""

/** Sender et behandlingsutfall inn på ruta saksbehandling-api kaller. */
suspend fun ApplicationTestBuilder.postBehandlingsutfall(
    token: String?,
    body: String,
): HttpResponse = client.post("$SAKSBEHANDLING_PATH/soknad/utfall") {
    token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    contentType(ContentType.Application.Json)
    setBody(body)
}
