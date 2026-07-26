package no.nav.tiltakspenger.soknad.api

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.tid.zoneIdOslo
import java.time.Clock

/**
 * Starter serveren lokalt med postgres fra docker-compose og fakene i [LokalApplicationContext].
 * Kjører nøyaktig samme [start]-rutine som i drift — kun konteksten og bindingsadressen er annerledes.
 */
fun main() {
    System.setProperty("logback.configurationFile", Configuration.logbackConfigurationFile())

    val log = KotlinLogging.logger {}
    log.info { "Starter lokal server" }

    start(
        log = log,
        // Bind til loopback lokalt, slik at porten ikke eksponeres på nettverket.
        host = "127.0.0.1",
        isNais = false,
        applicationContext = LokalApplicationContext(Clock.system(zoneIdOslo)),
    )
}
