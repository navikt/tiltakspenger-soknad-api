package no.nav.tiltakspenger.soknad.api.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.Flyway

private val LOG = KotlinLogging.logger {}

/**
 * Oppretter datasourcen og kjører Flyway-migreringene.
 * jdbc-url-en tas inn som parameter (ikke lest fra global config her), slik at oppsettet kan testes mot en testcontainer uten å mutere global tilstand — jf. mønsteret i de andre backend-repoene.
 */
object DataSourceSetup {
    fun createDatasource(jdbcUrl: String): HikariDataSource {
        LOG.info { "Kobler til Postgres. Bruker bare jdbc-urlen i config (+ timeout og maxpools)." }
        return HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                initializationFailTimeout = 5000
                connectionTimeout = 2000
                minimumIdle = 5
                maximumPoolSize = 10
            },
        ).also {
            LOG.info { "Starter Flyway-migrering" }
            Flyway
                .configure()
                .loggers("slf4j")
                .encoding("UTF-8")
                .dataSource(it)
                .load()
                .migrate()
            LOG.info { "Flyway-migrering utført" }
        }
    }
}
