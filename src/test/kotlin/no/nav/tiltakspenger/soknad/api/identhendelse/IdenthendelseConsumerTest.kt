package no.nav.tiltakspenger.soknad.api.identhendelse

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.random
import no.nav.tiltakspenger.libs.json.serialize
import no.nav.tiltakspenger.libs.kafka.config.LocalKafkaConfig
import no.nav.tiltakspenger.soknad.api.soknad.Applikasjonseier
import no.nav.tiltakspenger.soknad.api.testutils.FakeSøknadRepo
import no.nav.tiltakspenger.soknad.api.util.genererMottattSøknadForTest
import org.junit.jupiter.api.Test
import java.util.UUID

/** Ende-til-ende for identhendelser: fra Kafka-meldingen, gjennom servicen og ned i repoet. */
class IdenthendelseConsumerTest {
    private val søknadRepo = FakeSøknadRepo()

    private fun consumer(topic: String) = IdenthendelseConsumer(
        identhendelseService = IdenthendelseService(søknadRepo),
        topic = topic,
        kafkaConfig = LocalKafkaConfig(),
    )

    @Test
    fun `consume oppdaterer fødselsnummeret på brukerens søknader, og lar andre være i fred`() = runTest {
        val gammeltFnr = Fnr.random()
        val nyttFnr = Fnr.random()
        val urelatertFnr = Fnr.random()
        val søknad = genererMottattSøknadForTest(fnr = gammeltFnr.verdi, eier = Applikasjonseier.Tiltakspenger)
        val annenBrukersSøknad = genererMottattSøknadForTest(fnr = urelatertFnr.verdi, eier = Applikasjonseier.Tiltakspenger)
        søknadRepo.lagre(søknad)
        søknadRepo.lagre(annenBrukersSøknad)
        val identhendelse = IdenthendelseDto(gammeltFnr = gammeltFnr.verdi, nyttFnr = nyttFnr.verdi)

        consumer("identhendelse-oppdater").consume(UUID.randomUUID(), serialize(identhendelse))

        søknadRepo.hentSøknad(søknad.id)?.fnr shouldBe nyttFnr.verdi
        søknadRepo.hentSøknad(annenBrukersSøknad.id)?.fnr shouldBe urelatertFnr.verdi
    }

    @Test
    fun `run starter og stop stopper consumeren uten kjørende broker`() {
        val consumer = consumer("identhendelse-run-stop")

        val job = consumer.run()
        consumer.stop()

        job.isCompleted shouldBe true
    }

    @Test
    fun `default kafka-config utenfor Nais er LocalKafkaConfig`() {
        // Utelatt kafkaConfig-parameter evaluerer defaulten (LocalKafkaConfig utenfor Nais) ved konstruksjon.
        shouldNotThrowAny {
            IdenthendelseConsumer(
                identhendelseService = IdenthendelseService(søknadRepo),
                topic = "identhendelse-default-config",
            )
        }
    }
}
