package no.nav.tiltakspenger.soknad.api.identhendelse

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.json.serialize
import no.nav.tiltakspenger.libs.kafka.config.LocalKafkaConfig
import org.junit.jupiter.api.Test
import java.util.UUID

class IdenthendelseConsumerTest {
    private val identhendelseService = mockk<IdenthendelseService>()

    @Test
    fun `consume deserialiserer hendelsen og sender den til servicen`() = runTest {
        val consumer = IdenthendelseConsumer(
            identhendelseService = identhendelseService,
            topic = "test-topic",
            kafkaConfig = LocalKafkaConfig(),
        )
        val id = UUID.randomUUID()
        val identhendelse = IdenthendelseDto(gammeltFnr = "12345678910", nyttFnr = "10987654321")
        justRun { identhendelseService.behandleIdenthendelse(id, identhendelse) }

        consumer.consume(id, serialize(identhendelse))

        io.mockk.verify { identhendelseService.behandleIdenthendelse(id, identhendelse) }
    }

    @Test
    fun `run starter og stop stopper consumeren uten kjorende broker`() {
        val consumer = IdenthendelseConsumer(
            identhendelseService = identhendelseService,
            topic = "test-topic-run-stop",
            kafkaConfig = LocalKafkaConfig(),
        )

        val job = consumer.run()
        consumer.stop()

        job.isCompleted shouldBe true
    }

    @Test
    fun `default kafka-config utenfor Nais er LocalKafkaConfig`() {
        // Utelatt kafkaConfig-parameter evaluerer defaulten (LocalKafkaConfig utenfor Nais) ved konstruksjon.
        shouldNotThrowAny {
            IdenthendelseConsumer(
                identhendelseService = identhendelseService,
                topic = "test-topic-default-config",
            )
        }
    }
}
