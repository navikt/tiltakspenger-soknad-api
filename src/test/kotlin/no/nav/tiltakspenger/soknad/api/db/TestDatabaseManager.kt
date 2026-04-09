package no.nav.tiltakspenger.soknad.api.db

import no.nav.tiltakspenger.libs.persistering.test.common.TestDatabaseConfig
import javax.sql.DataSource
import no.nav.tiltakspenger.libs.persistering.test.common.TestDatabaseManager as LibsTestDatabaseManager

internal class TestDatabaseManager(
    config: TestDatabaseConfig = TestDatabaseConfig(),
) {
    private val delegate = LibsTestDatabaseManager(
        config = config,
        idGeneratorsFactory = { },
    )

    fun withMigratedDb(
        runIsolated: Boolean = false,
        test: (DataSource) -> Unit,
    ) {
        delegate.withMigratedDb(runIsolated = runIsolated) { _, _, _ ->
            test(delegate.dataSource(runIsolated))
        }
    }
}

internal val testDatabaseManager = TestDatabaseManager()
