package no.nav.tiltakspenger.soknad.api

import no.nav.tiltakspenger.libs.jobber.TaskResultat
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Bakgrunnsprosessoppsett
import no.nav.tiltakspenger.libs.ktor.common.oppstart.KafkaConsumerOppsett
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Miljøverdi
import no.nav.tiltakspenger.libs.ktor.common.oppstart.Task
import kotlin.time.Duration.Companion.seconds

/**
 * Alt appen kjører i bakgrunnen, samlet ett sted.
 * Ligger utenfor `start()` slik at både drift, lokal kjøring og tester setter opp nøyaktig de samme jobbene og consumerne.
 */
fun bakgrunnsprosessoppsett(
    applicationContext: ApplicationContext,
    isNais: Boolean,
): Bakgrunnsprosessoppsett = Bakgrunnsprosessoppsett(
    mdcCallIdKey = CALL_ID_MDC_KEY,
    electorPath = Configuration::electorPath,
    tasks = jobber(applicationContext),
    kafkaConsumers = kafkaConsumers(isNais = isNais, applicationContext = applicationContext),
    clock = applicationContext.clock,
)

fun jobber(applicationContext: ApplicationContext): List<Task> = listOf(
    Task(
        navn = "soknad-jobb-hent-saksnummer",
        intervall = Miljøverdi.lik(60.seconds),
        utfør = { correlationId ->
            applicationContext.søknadJobbService.hentEllerOpprettSaksnummer(correlationId)
            TaskResultat.Ferdig
        },
    ),
    Task(
        navn = "soknad-jobb-journalfør",
        intervall = Miljøverdi.lik(60.seconds),
        utfør = { correlationId ->
            applicationContext.søknadJobbService.journalførLagredeSøknader(correlationId)
            TaskResultat.Ferdig
        },
    ),
    Task(
        navn = "soknad-jobb-send-til-saksbehandling",
        intervall = Miljøverdi.lik(60.seconds),
        utfør = { correlationId ->
            applicationContext.søknadJobbService.sendJournalførteSøknaderTilSaksbehandlingApi(correlationId)
            TaskResultat.Ferdig
        },
    ),
)

/**
 * Identhendelse-consumeren kjører kun i Nais.
 * Lokalt har vi ingen Kafka, så lista er tom og consumeren blir aldri konstruert.
 */
fun kafkaConsumers(
    isNais: Boolean,
    applicationContext: ApplicationContext,
): List<KafkaConsumerOppsett> = if (isNais) {
    listOf(
        KafkaConsumerOppsett(
            navn = "identhendelse-consumer",
            start = { applicationContext.identhendelseConsumer.run() },
            stopp = { applicationContext.identhendelseConsumer.stop() },
        ),
    )
} else {
    emptyList()
}
