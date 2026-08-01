package no.nav.tiltakspenger.soknad.api.soknad.routes

import io.kotest.matchers.shouldBe
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Readiness
import no.nav.tiltakspenger.soknad.api.LokalApplicationContext
import no.nav.tiltakspenger.soknad.api.SØKNAD_PATH
import no.nav.tiltakspenger.soknad.api.ktorSetup
import no.nav.tiltakspenger.soknad.api.soknad.validering.spørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.soknad.validering.toJsonString
import no.nav.tiltakspenger.soknad.api.testutils.FakeSøknadRepo
import no.nav.tiltakspenger.soknad.api.testutils.enkelPdf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Den eneste testen i repoet som går over en ekte sokkel, mot en ekte Netty-server.
 *
 * `testApplication` kjører i minnet uten Nettys event loop, og der er en blokkerende lesing av request-bodyen harmløs.
 * I drift er den ikke det: leser vi vedlegget med den utgåtte `streamProvider`, blokkerer event loop-tråden mens den venter på bytes som den samme tråden skulle levert, og innsendingen står til klienten gir opp.
 * Testen sender derfor bodyen i to omganger, slik at vedlegget garantert ikke er ferdig mottatt når ruta begynner å lese det.
 *
 * Feiler den med timeout, er lesingen blokkerende igjen.
 */
class SøknadMultipartOverEkteServerTest {
    private val grense = "GrenseForTest"

    /**
     * En vranglås her låser både lesingen fra sokkelen og nedstengingen av serveren, så uten en hard grense ville testen hengt i stedet for å feile.
     * [Timeout.ThreadMode.SEPARATE_THREAD] er det som skal til: default-modusen venter på at testen blir ferdig og rapporterer først etterpå, altså aldri når tråden står fast.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `tar imot vedlegg som kommer i flere omganger`() {
        val søknadRepo = FakeSøknadRepo()
        val context = LokalApplicationContext(
            clock = fixedClock,
            søknadRepo = søknadRepo,
            collectorRegistry = CollectorRegistry(),
        )
        // Port 0 lar OS-et velge en ledig port, slik at testen ikke kolliderer med en lokal server.
        val server = embeddedServer(Netty, port = 0) { ktorSetup(context, Readiness()) }
        server.start(wait = false)

        try {
            val port = runBlocking { server.engine.resolvedConnectors().first().port }

            val statuslinje = sendSøknadIToOmganger(port)

            statuslinje shouldBe "HTTP/1.1 201 Created"
            søknadRepo.alle.single().vedlegg.single().filnavn shouldBe "vedlegg-1.pdf"
        } finally {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 1.seconds.inWholeMilliseconds)
        }
    }

    /**
     * Sender multipart-bodyen delt i to skrivinger med en pause imellom.
     * Første del slutter midt i vedlegget: ruta har da fått nok til å begynne å lese fila, men ikke hele.
     * Blokkerer lesingen event loopen, blir resten aldri lest, og [Socket.setSoTimeout] slår inn i stedet for at testen henger.
     */
    private fun sendSøknadIToOmganger(port: Int): String {
        val pdf = enkelPdf()
        val innledning = (
            "--$grense\r\n" +
                "Content-Disposition: form-data; name=\"søknad\"\r\n" +
                "Content-Type: application/json\r\n\r\n" +
                spørsmålsbesvarelser().toJsonString() + "\r\n" +
                "--$grense\r\n" +
                "Content-Disposition: form-data; name=\"vedlegg\"; filename=\"vedlegg-1.pdf\"\r\n" +
                "Content-Type: application/pdf\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)
        val avslutning = "\r\n--$grense--\r\n".toByteArray(Charsets.UTF_8)
        val body = innledning + pdf + avslutning

        val hode = (
            "POST $SØKNAD_PATH HTTP/1.1\r\n" +
                "Host: 127.0.0.1:$port\r\n" +
                "Authorization: Bearer et-hvilket-som-helst-token\r\n" +
                "Content-Type: multipart/form-data; boundary=$grense\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)

        // Delen som holdes tilbake må ligge inne i vedlegget, ellers rekker ruta aldri å begynne å lese fila.
        val førsteDel = hode + innledning + pdf.copyOfRange(0, PDF_BYTES_I_FØRSTE_SKRIVING)
        val andreDel = pdf.copyOfRange(PDF_BYTES_I_FØRSTE_SKRIVING, pdf.size) + avslutning

        Socket(InetAddress.getLoopbackAddress(), port).use { sokkel ->
            sokkel.soTimeout = LESETIDSAVBRUDD_MILLIS
            val ut = sokkel.getOutputStream()

            ut.write(førsteDel)
            ut.flush()
            // Pausen gir Netty tid til å levere første del til ruta før resten kommer.
            Thread.sleep(PAUSE_MELLOM_SKRIVINGER_MILLIS)
            ut.write(andreDel)
            ut.flush()

            return sokkel.getInputStream().bufferedReader(Charsets.UTF_8).readLine().orEmpty()
        }
    }

    private companion object {
        const val PDF_BYTES_I_FØRSTE_SKRIVING = 32
        const val PAUSE_MELLOM_SKRIVINGER_MILLIS = 300L

        /** Rikelig for et lokalt kall som virker, og kort nok til at en vranglås ikke stopper hele testkjøringen. */
        const val LESETIDSAVBRUDD_MILLIS = 15_000
    }
}
