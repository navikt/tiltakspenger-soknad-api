package no.nav.tiltakspenger.soknad.api.db

import org.flywaydb.core.Flyway

fun flywayMigrate() {
    Flyway
        .configure()
        .loggers("slf4j")
        .encoding("UTF-8")
        .dataSource(DataSource.dataSource)
        .load()
        .migrate()
}
