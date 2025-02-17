package no.nav.tiltakspenger.soknad.api.jobber

import arrow.core.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.jobber.RunCheckFactory
import no.nav.tiltakspenger.libs.jobber.StoppableJob
import no.nav.tiltakspenger.libs.jobber.startStoppableJob
import no.nav.tiltakspenger.libs.logging.sikkerlogg
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Generisk task executor som kjører tasks med gitt intervall.
 * Tanken er at den kan brukes til å kjøre tasks som genererer meldekort, sender brev, etc.
 */
internal class TaskExecutor(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            runCheckFactory: RunCheckFactory,
            tasks: List<suspend (CorrelationId) -> Unit>,
            initialDelay: Duration = 1.minutes,
            intervall: Duration = 60.seconds,
        ): TaskExecutor {
            val log = KotlinLogging.logger { }
            return TaskExecutor(
                startStoppableJob(
                    jobName = "Jobb som kjører hvert 60. sekund",
                    initialDelay = initialDelay.toJavaDuration(),
                    intervall = intervall.toJavaDuration(),
                    logger = log,
                    sikkerLogg = sikkerlogg,
                    // Ref callIdMdc("call-id") i VedtakApi.kt
                    mdcCallIdKey = "call-id",
                    runJobCheck = listOf(runCheckFactory.leaderPod()),
                    // Denne kjører så ofte at vi ønsker ikke bli spammet av logging.
                    enableDebuggingLogging = false,
                    job = { correlationId ->
                        tasks.forEach {
                            CoroutineScope(Dispatchers.IO).launch {
                                // Vi ønsker ikke at en task skal spenne ben for andre tasks.
                                Either.catch {
                                    it(correlationId)
                                }.onLeft {
                                    log.error(it) { "Feil ved kjøring av task. correlationId: $correlationId" }
                                }
                            }
                        }
                    },
                ),
            )
        }
    }
}
