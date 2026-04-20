package no.nav.tiltakspenger.soknad.api.repository

import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.soknad.api.db.testDatabaseManager
import no.nav.tiltakspenger.soknad.api.soknad.Applikasjonseier
import no.nav.tiltakspenger.soknad.api.soknad.RegistrertBarn
import no.nav.tiltakspenger.soknad.api.soknad.SøknadRepo
import no.nav.tiltakspenger.soknad.api.soknad.validering.barnetillegg
import no.nav.tiltakspenger.soknad.api.soknad.validering.spørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.soknad.validering.søknad
import no.nav.tiltakspenger.soknad.api.util.genererMottattSøknadForTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

internal class SøknadRepoTest {
    private fun withCleanDb(test: (SøknadRepo) -> Unit) {
        testDatabaseManager.withMigratedDb(runIsolated = true) { dataSource ->
            test(SøknadRepo(dataSource))
        }
    }

    private fun withCleanDbAndDataSource(test: (SøknadRepo, DataSource) -> Unit) {
        testDatabaseManager.withMigratedDb(runIsolated = true) { dataSource ->
            test(SøknadRepo(dataSource), dataSource)
        }
    }

    @Nested
    inner class KanLagreSøknad {
        @Test
        fun `lagrer en helt vanlig søknad`() = withCleanDb { søknadRepo ->
            val nå = LocalDateTime.now(fixedClock)
            val mottattSøknad = genererMottattSøknadForTest(
                opprettet = nå,
                eier = Applikasjonseier.Tiltakspenger,
            )
            søknadRepo.lagre(mottattSøknad)
            val actual = søknadRepo.hentSoknad(mottattSøknad.id)
            actual shouldBe mottattSøknad
        }

        @Test
        fun `kan lagre og hente en søknad som ikke har fnr i barnetillegg`() = withCleanDb { søknadRepo ->
            val nå = LocalDateTime.now(fixedClock)
            val mottattSøknad = genererMottattSøknadForTest(
                opprettet = nå,
                eier = Applikasjonseier.Tiltakspenger,
                søknadSpm = spørsmålsbesvarelser(
                    barnetillegg = barnetillegg(
                        registrerteBarnSøktBarnetilleggFor = listOf(
                            RegistrertBarn(
                                fnr = null,
                                fornavn = "quot",
                                mellomnavn = "latine",
                                etternavn = "conclusionemque",
                                fødselsdato = LocalDate.now(fixedClock),
                                oppholdInnenforEøs = false,
                            ),
                        ),
                    ),
                ),
            )
            søknadRepo.lagre(mottattSøknad)
            val actual = søknadRepo.hentSoknad(mottattSøknad.id)
            actual shouldBe mottattSøknad
        }

        @Test
        fun `kan lagre og hente en søknad som har fnr i barnetillegg`() = withCleanDb { søknadRepo ->
            val nå = LocalDateTime.now(fixedClock)
            val mottattSøknad = genererMottattSøknadForTest(
                opprettet = nå,
                eier = Applikasjonseier.Tiltakspenger,
                søknadSpm = spørsmålsbesvarelser(
                    barnetillegg = barnetillegg(
                        registrerteBarnSøktBarnetilleggFor = listOf(
                            RegistrertBarn(
                                fnr = "potenti",
                                fornavn = "quot",
                                mellomnavn = "latine",
                                etternavn = "conclusionemque",
                                fødselsdato = LocalDate.now(fixedClock),
                                oppholdInnenforEøs = false,
                            ),
                        ),
                    ),
                ),
            )
            søknadRepo.lagre(mottattSøknad)
            val actual = søknadRepo.hentSoknad(mottattSøknad.id)
            actual shouldBe mottattSøknad
        }
    }

    @Test
    fun `lagrer mottat søknad, journalfører, og sender til sbh-api`() = withCleanDb { søknadRepo ->
        val nå = LocalDateTime.now()
        val søknad = søknad()
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = nå,
            eier = Applikasjonseier.Tiltakspenger,
        )
        søknadRepo.lagre(mottattSøknad)

        søknadRepo.hentAlleSøknadDbDtoSomIkkeErJournalført().size shouldBe 0

        val soknaderUtenSaksnummer = søknadRepo.hentSoknaderUtenSaksnummer()
        soknaderUtenSaksnummer.size shouldBe 1

        // Oppdaterer med saksnummer
        val soknadMedSaksnummer = soknaderUtenSaksnummer.first().copy(
            saksnummer = "12345",
        )
        søknadRepo.oppdater(soknadMedSaksnummer)

        søknadRepo.hentSoknaderUtenSaksnummer().size shouldBe 0

        val søknaderSomIkkeErJounalført = søknadRepo.hentAlleSøknadDbDtoSomIkkeErJournalført()
        søknaderSomIkkeErJounalført.size shouldBe 1

        // Journalfører søknaden
        val journalførtSøknad = søknaderSomIkkeErJounalført.first().copy(
            søknad = søknad,
            fornavn = "fornavn",
            etternavn = "etternavn",
            journalført = nå,
            journalpostId = JournalpostId("123"),
        )
        søknadRepo.oppdater(journalførtSøknad)
        søknadRepo.hentAlleSøknadDbDtoSomIkkeErJournalført().size shouldBe 0

        // sender søknaden til saksbehandling-api
        val søknaderSomIkkeErSendtTilSaksbehandlingApi = søknadRepo.hentSøknaderSomSkalSendesTilSaksbehandlingApi()
        søknaderSomIkkeErSendtTilSaksbehandlingApi.size shouldBe 1
        val søknadSendtTilSaksbehandlingApi = søknaderSomIkkeErSendtTilSaksbehandlingApi.first().copy(
            sendtTilVedtak = nå,
        )
        søknadRepo.oppdater(søknadSendtTilSaksbehandlingApi)
        søknadRepo.hentSøknaderSomSkalSendesTilSaksbehandlingApi().size shouldBe 0
    }

    @Test
    fun `søknad til arena`() = withCleanDb { søknadRepo ->
        val nå = LocalDateTime.now()
        val søknad = søknad()
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = nå,
            eier = Applikasjonseier.Arena,
        )
        søknadRepo.lagre(mottattSøknad)

        søknadRepo.hentSoknaderUtenSaksnummer().size shouldBe 0

        val søknaderSomIkkeErJounalført = søknadRepo.hentAlleSøknadDbDtoSomIkkeErJournalført()
        søknaderSomIkkeErJounalført.size shouldBe 1

        // Journalfører søknaden
        val journalførtSøknad = søknaderSomIkkeErJounalført.first().copy(
            søknad = søknad,
            fornavn = "fornavn",
            etternavn = "etternavn",
            journalført = nå,
            journalpostId = JournalpostId("123"),
        )
        søknadRepo.oppdater(journalførtSøknad)
        søknadRepo.hentAlleSøknadDbDtoSomIkkeErJournalført().size shouldBe 0

        // sender søknaden til saksbehandling-api
        val søknaderSomIkkeErSendtTilSaksbehandlingApi = søknadRepo.hentSøknaderSomSkalSendesTilSaksbehandlingApi()
        søknaderSomIkkeErSendtTilSaksbehandlingApi.size shouldBe 0
    }

    @Test
    fun `hent brukers søknader`() = withCleanDb { søknadRepo ->
        val nå = LocalDateTime.now()
        val fnr = "12345678910"
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = nå,
            eier = Applikasjonseier.Tiltakspenger,
            fnr = fnr,
        )
        søknadRepo.lagre(mottattSøknad)

        val brukersSøknader = søknadRepo.hentBrukersSøknader(fnr, Applikasjonseier.Tiltakspenger)
        brukersSøknader.size shouldBe 1
        brukersSøknader.first().fnr shouldBe fnr
        brukersSøknader.first().eier shouldBe Applikasjonseier.Tiltakspenger
    }

    @Test
    fun `lagrer journalpostId som ren streng i databasen`() = withCleanDbAndDataSource { søknadRepo, dataSource ->
        val journalpostId = JournalpostId("123")
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = LocalDateTime.now(),
            eier = Applikasjonseier.Tiltakspenger,
        ).copy(
            søknad = søknad(),
            fornavn = "fornavn",
            etternavn = "etternavn",
            journalført = LocalDateTime.now(),
            journalpostId = journalpostId,
        )

        søknadRepo.lagre(mottattSøknad)

        val lagretJournalpostId = sessionOf(dataSource).use {
            it.run(
                queryOf(
                    "select journalpostId from søknad where id = :id",
                    mapOf("id" to mottattSøknad.id.toString()),
                ).map { row ->
                    row.string("journalpostId")
                }.asSingle,
            )
        }

        lagretJournalpostId shouldBe journalpostId.toString()
    }
}
