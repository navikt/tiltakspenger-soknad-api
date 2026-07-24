package no.nav.tiltakspenger.soknad.api.db

import io.kotest.matchers.ints.shouldBeGreaterThan
import org.junit.jupiter.api.Test
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource

class DataSourceSetupTest {
    @Test
    fun `oppretter datasource fra jdbc-url og kjører Flyway-migreringene`() {
        // jdbc-url-en tas inn som parameter, så testen trenger ingen mutasjon av global system-env.
        PostgreSQLContainer("postgres:17-alpine").use { postgres ->
            postgres.start()
            val skilletegn = if (postgres.jdbcUrl.contains('?')) "&" else "?"
            val jdbcUrl = "${postgres.jdbcUrl}${skilletegn}user=${postgres.username}&password=${postgres.password}"

            DataSourceSetup.createDatasource(jdbcUrl).use { dataSource ->
                antallKjørteMigreringer(dataSource) shouldBeGreaterThan 0
            }
        }
    }

    private fun antallKjørteMigreringer(dataSource: DataSource): Int =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from flyway_schema_history where success").use { resultSet ->
                    resultSet.next()
                    resultSet.getInt(1)
                }
            }
        }
}
