package no.nav.tiltakspenger.soknad.api

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.tiltakspenger.libs.ktor.common.oppstart.startApp
import no.nav.tiltakspenger.libs.tid.zoneIdOslo
import no.nav.tiltakspenger.soknad.api.db.DataSourceSetup
import no.nav.tiltakspenger.soknad.api.soknad.SøknadPostgresRepo
import java.time.Clock

fun main() {
    System.setProperty("logback.configurationFile", Configuration.logbackConfigurationFile)

    val log = KotlinLogging.logger {}
    log.info { "starting server" }
    start(log)
}

/**
 * Oppstartsrutinen, delt mellom drift og lokal kjøring.
 * [applicationContext] bygges by default fra [Configuration] og en ekte datasource; lokal kjøring (`LokalMain`) sender inn sin egen kontekst med fakes.
 *
 * Selve innholdet — jobbene, Kafka-consumerne og Ktor-oppsettet — ligger i [bakgrunnsprosessoppsett] og [ktorSetup], slik at tester kan kjøre nøyaktig den samme oppkoblingen uten en Netty-server.
 *
 * Her konstrueres også registeret alle appens målinger føres i: Ktor-metrikkene, appens egne tellere, jobbmålingene og meldingsleser-målingene.
 * Det er det samme registeret `/metrics` skraper, så sender vi inn et annet register ett av stedene, forsvinner seriene stille.
 * Registeret er appens eget og bindes ikke til Prometheus sitt globale register, siden ingenting i dette repoet registrerer målinger der.
 * Test- og lokalkontekstene lager sitt eget, fordi et prosessnavn bare kan registreres én gang per register.
 */
fun start(
    log: KLogger,
    port: Int = Configuration.httpPort,
    host: String = "0.0.0.0",
    isNais: Boolean = Configuration.isNais(),
    applicationContext: ApplicationContext = ApplicationContext(
        clock = Clock.system(zoneIdOslo),
        søknadRepo = SøknadPostgresRepo(DataSourceSetup.createDatasource(Configuration.database().url)),
        meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
    ),
) {
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        log.error(e) { e.message }
    }
    log.info { "starting server" }

    startApp(
        log = log,
        port = port,
        host = host,
        isNais = isNais,
        oppsett = bakgrunnsprosessoppsett(applicationContext = applicationContext, isNais = isNais),
    ) { readiness ->
        ktorSetup(applicationContext, readiness)
    }
}
