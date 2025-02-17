package no.nav.tiltakspenger.soknad.api.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.tiltakspenger.soknad.api.Configuration.database

object DataSource {
    private val config = database()
    private const val MAX_POOLS = 3
    const val FAIL_TIMEOUT = 5000

    private fun init(): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = config.url
            maximumPoolSize = MAX_POOLS
            initializationFailTimeout = FAIL_TIMEOUT.toLong()
        }

        return HikariDataSource(config)
    }

    val hikariDataSource: HikariDataSource by lazy {
        init()
    }
}
