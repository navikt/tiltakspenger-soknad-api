package no.nav.tiltakspenger.soknad.api.identhendelse

import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.random
import no.nav.tiltakspenger.soknad.api.db.testDatabaseManager
import no.nav.tiltakspenger.soknad.api.soknad.Applikasjonseier
import no.nav.tiltakspenger.soknad.api.soknad.SøknadRepo
import no.nav.tiltakspenger.soknad.api.util.genererMottattSøknadForTest
import org.junit.jupiter.api.Test
import java.util.UUID

class IdenthendelseServiceTest {
    @Test
    fun `behandleIdenthendelse - finnes søknad på gammelt fnr - oppdaterer`() {
        testDatabaseManager.withMigratedDb(runIsolated = true) { dataSource ->
            val søknadRepo = SøknadRepo(dataSource)
            val identhendelseService = IdenthendelseService(søknadRepo)

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
}
