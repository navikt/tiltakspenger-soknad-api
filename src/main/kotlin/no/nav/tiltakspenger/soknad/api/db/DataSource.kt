package no.nav.tiltakspenger.soknad.api.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.tiltakspenger.soknad.api.Configuration.database
import javax.sql.DataSource

object DataSource {
    private val config = database()

    private fun init(): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = config.url
            initializationFailTimeout = 5000
            connectionTimeout = 2000
            minimumIdle = 5
            maximumPoolSize = 10
        }

        return HikariDataSource(config)
    }

    val dataSource: DataSource by lazy {
        init()
    }
}
