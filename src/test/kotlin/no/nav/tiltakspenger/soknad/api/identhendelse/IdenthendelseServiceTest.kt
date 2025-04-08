package no.nav.tiltakspenger.soknad.api.identhendelse

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.random
import no.nav.tiltakspenger.soknad.api.db.DataSource
import no.nav.tiltakspenger.soknad.api.db.PostgresTestcontainer
import no.nav.tiltakspenger.soknad.api.soknad.Applikasjonseier
import no.nav.tiltakspenger.soknad.api.soknad.SøknadRepo
import no.nav.tiltakspenger.soknad.api.util.genererMottattSøknadForTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Testcontainers
class IdenthendelseServiceTest {
    private val søknadRepo = SøknadRepo()
    private val identhendelseService = IdenthendelseService(søknadRepo)

    init {
        PostgresTestcontainer.start()
    }

    @BeforeEach
    fun setup() {
        Flyway.configure()
            .dataSource(DataSource.hikariDataSource)
            .loggers("slf4j")
            .encoding("UTF-8")
            .cleanDisabled(false)
            .load()
            .run {
                clean()
                migrate()
            }
    }

    @Test
    fun `behandleIdenthendelse - finnes søknad på gammelt fnr - oppdaterer`() {
        val gammeltFnr = Fnr.random()
        val nyttFnr = Fnr.random()
        val mottattSoknad = genererMottattSøknadForTest(
            fnr = gammeltFnr.verdi,
            eier = Applikasjonseier.Tiltakspenger,
        )
        søknadRepo.lagre(mottattSoknad)
        val urelatertFnr = Fnr.random()
        val mottattSoknadAnnenBruker = genererMottattSøknadForTest(
            fnr = urelatertFnr.verdi,
            eier = Applikasjonseier.Tiltakspenger,
        )
        søknadRepo.lagre(mottattSoknadAnnenBruker)

        identhendelseService.behandleIdenthendelse(
            id = UUID.randomUUID(),
            identhendelseDto = IdenthendelseDto(gammeltFnr = gammeltFnr.verdi, nyttFnr = nyttFnr.verdi),
        )

        søknadRepo.hentSoknad(mottattSoknad.id)?.fnr shouldBe nyttFnr.verdi
        søknadRepo.hentSoknad(mottattSoknadAnnenBruker.id)?.fnr shouldBe urelatertFnr.verdi
    }
}
