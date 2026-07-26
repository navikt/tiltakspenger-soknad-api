package no.nav.tiltakspenger.soknad.api.testutils

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.TransportRespons
import no.nav.tiltakspenger.libs.json.serialize
import java.net.http.HttpRequest
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Transporten [no.nav.tiltakspenger.soknad.api.LokalApplicationContext] kjører med.
 * Svarer med faste, gyldige svar per endepunkt, slik at hele søknadsflyten — personalia, tiltak, virussjekk, PDF, journalføring og oversending — virker lokalt uten at noe annet enn postgres kjører.
 *
 * Til forskjell fra `FakeHttpTransport`, som er en FIFO-kø for én test, ruter denne på URI og kan svare på ubegrenset mange kall.
 * Klientene er de ekte, så hele klient-pipelinen kjører også lokalt.
 */
class LokalHttpTransport(
    private val clock: Clock,
    private val fnr: String,
    /**
     * Stien ClamAV-endepunktet ligger på.
     * De andre tjenestene kjennes igjen på stier som er en del av API-et deres, mens ClamAV kalles rett på rot-URL-en — som er `/av` i den lokale konfigurasjonen.
     */
    private val avPath: String = "/av",
) : HttpTransport {
    private val log = KotlinLogging.logger { }

    /** Barn under 16 år, slik at barnetillegg kan fylles ut lokalt. */
    private val barn: Map<String, String>
        get() = mapOf(
            "01011012345" to LocalDate.now(clock).minusYears(8).toString(),
            "02022014567" to LocalDate.now(clock).minusYears(12).toString(),
        )

    override suspend fun send(request: HttpRequest): TransportRespons {
        val path = request.uri().path
        return when {
            path.endsWith("/graphql") -> json(
                søkerOgBarnRespons(
                    fornavn = "Lokal",
                    etternavn = "Lokalsen",
                    fødselsdato = LocalDate.now(clock).minusYears(35).toString(),
                    barn = barn,
                ),
            )

            path.endsWith("/tiltakshistorikk") -> json(serialize(listOf(lokaltTiltak())))

            // ClamAV svarer med ett resultat per opplastet fil; «OK» betyr rent vedlegg.
            path == avPath -> json("""[{"Filename":"vedlegg","Result":"OK"}]""")

            path.contains("genpdf") -> TransportRespons(
                statusCode = 200,
                headere = mapOf("Content-Type" to listOf("application/pdf")),
                body = enkelPdf(),
            )

            path.contains("journalpost") -> {
                val skalFerdigstilles = request.uri().query?.contains("forsoekFerdigstill=true") == true
                json("""{"journalpostId":"${UUID.randomUUID().toString().take(8)}","journalpostferdigstilt":$skalFerdigstilles}""")
            }

            path.endsWith("/saksnummer") -> json("""{"saksnummer":"${saksnummer()}"}""")

            // Oversending av søknad til saksbehandling-api kvitterer med 200 uten body.
            path.endsWith("/soknad") -> TransportRespons(200, emptyMap(), ByteArray(0))

            else -> {
                log.warn { "LokalHttpTransport har ikke noe svar for ${request.method()} ${request.uri()} — svarer 404." }
                TransportRespons(404, emptyMap(), ByteArray(0))
            }
        }
    }

    private fun lokaltTiltak() = tiltakshistorikk(
        arrangør = "Lokal arrangør AS",
        // Innenfor vinduet ruta filtrerer på, slik at tiltaket faktisk vises.
        deltakelseFom = LocalDate.now(clock).minusMonths(1),
    )

    private fun saksnummer() = "202501${fnr.take(4)}"

    private fun json(body: String) = TransportRespons(
        statusCode = 200,
        headere = mapOf("Content-Type" to listOf("application/json")),
        body = body.toByteArray(),
    )
}
