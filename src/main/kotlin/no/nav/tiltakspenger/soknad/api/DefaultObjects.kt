package no.nav.tiltakspenger.soknad.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.serialization.jackson3.JacksonConverter
import no.nav.tiltakspenger.libs.json.objectMapper
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import java.time.Duration

private val log = KotlinLogging.logger {}
private const val SIXTY_SECONDS = 60L
fun httpClientCIO(timeout: Long = SIXTY_SECONDS) = HttpClient(CIO).config(timeout)
fun httpClientGeneric(engine: HttpClientEngine, timeout: Long = SIXTY_SECONDS) = HttpClient(engine).config(timeout)
fun httpClientWithRetry(timeout: Long = SIXTY_SECONDS) = httpClientCIO(timeout).also { httpClient ->
    httpClient.config {
        install(HttpRequestRetry) {
            maxRetries = 3
            retryIf { request, response ->
                if (response.status.value.let { it in 500..599 }) {
                    log.warn { "Http-kall feilet med ${response.status.value}. Kjører retry" }
                    true
                } else {
                    false
                }
            }
            retryOnExceptionIf { request, throwable ->
                log.warn(throwable) { "Kastet exception ved http-kall: ${throwable.message}" }
                true
            }
            constantDelay(100, 0, false)
        }
    }
}

private fun HttpClient.config(timeout: Long) = this.config {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(objectMapper))
    }
    install(HttpTimeout) {
        connectTimeoutMillis = Duration.ofSeconds(timeout).toMillis()
        requestTimeoutMillis = Duration.ofSeconds(timeout).toMillis()
        socketTimeoutMillis = Duration.ofSeconds(timeout).toMillis()
    }
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                Sikkerlogg.debug { message }
            }
        }
        level = LogLevel.INFO
    }
    expectSuccess = true
}
