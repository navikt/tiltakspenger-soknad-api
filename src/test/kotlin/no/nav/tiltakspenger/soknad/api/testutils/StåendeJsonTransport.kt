package no.nav.tiltakspenger.soknad.api.testutils

import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.TransportRespons
import java.net.http.HttpRequest
import kotlin.time.Duration

/**
 * [HttpTransport] som svarer med den samme JSON-en hver gang.
 *
 * `FakeHttpTransport` fra libs er en FIFO-kø og hører hjemme i tester, der antall kall er kjent på forhånd.
 * Lokal kjøring gjør de samme kallene om og om igjen så lenge appen står, og trenger derfor et stående svar i stedet for en kø.
 *
 * Transporten byttes inn på produksjonsklienten, så auth, retry, statusregler og deserialisering kjører også lokalt — det er bare selve nettverket som er borte.
 *
 * @param json Bygges per kall, slik at et svar kan bruke datoer relativt til klokka.
 */
class StåendeJsonTransport(
    private val json: () -> String,
) : HttpTransport {
    /** Faken kobler ikke opp noe, så det finnes ingen oppkoblings-grense å rapportere. */
    override val connectTimeout: Duration? = null

    override suspend fun send(request: HttpRequest): TransportRespons = TransportRespons(
        statusCode = 200,
        headere = mapOf("Content-Type" to listOf("application/json")),
        body = json().toByteArray(),
    )
}
