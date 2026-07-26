package no.nav.tiltakspenger.soknad.api

import arrow.core.flatMap
import arrow.core.getOrElse
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.lokal.LokalPostgresConfig
import no.nav.tiltakspenger.libs.lokal.somMelding
import no.nav.tiltakspenger.libs.lokal.startLokalPostgres
import no.nav.tiltakspenger.libs.tid.zoneIdOslo
import no.nav.tiltakspenger.soknad.api.db.DataSourceSetup
import no.nav.tiltakspenger.soknad.api.soknad.SøknadPostgresRepo
import java.time.Clock
import kotlin.system.exitProcess

/**
 * Starter serveren lokalt med postgres fra docker-compose og fakene i [LokalApplicationContext].
 * Kjører nøyaktig samme [start]-rutine som i drift — kun konteksten og bindingsadressen er annerledes.
 *
 * Postgres startes for deg hvis den ikke allerede kjører — se [startLokalPostgres].
 */
fun main() {
    System.setProperty("logback.configurationFile", Configuration.logbackConfigurationFile())

    val log = KotlinLogging.logger {}
    log.info { "Starter lokal server" }
    val clock = Clock.system(zoneIdOslo)

    val postgres = LokalPostgresConfig.fraJdbcUrl(
        jdbcUrl = Configuration.database().url,
        composeTjeneste = "postgresSoknad",
    ).map { config ->
        // Søknad har sitt eget compose-oppsett i monorepo-rota, ved siden av docker-compose.yml.
        config.copy(composefilnavn = listOf("docker-compose-soknad.yml"))
    }.flatMap { config ->
        startLokalPostgres(config = config, clock = clock)
    }.getOrElse { feil ->
        log.error(feil.årsak) { feil.somMelding() }
        exitProcess(1)
    }
    // Jdbc-url-en er den samme som i Configuration når vi kjører mot compose, men i testcontainers-modus er porten tilfeldig.
    System.setProperty("DB_JDBC_URL", postgres.jdbcUrl)
    log.info { "Lokal postgres er klar: ${postgres.beskrivelse}" }

    start(
        log = log,
        // Bind til loopback lokalt, slik at porten ikke eksponeres på nettverket.
        host = "127.0.0.1",
        isNais = false,
        applicationContext = LokalApplicationContext(
            clock = clock,
            søknadRepo = SøknadPostgresRepo(DataSourceSetup.createDatasource(Configuration.database().url)),
        ),
    )
}
