package no.nav.tiltakspenger.soknad.api.repository

import arrow.core.right
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.common.random
import no.nav.tiltakspenger.soknad.api.db.testDatabaseManager
import no.nav.tiltakspenger.soknad.api.soknad.Applikasjonseier
import no.nav.tiltakspenger.soknad.api.soknad.Oppholdssted
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import no.nav.tiltakspenger.soknad.api.soknad.RegistrertBarn
import no.nav.tiltakspenger.soknad.api.soknad.SpørsmålsbesvarelserDTO
import no.nav.tiltakspenger.soknad.api.soknad.SøknadPostgresRepo
import no.nav.tiltakspenger.soknad.api.soknad.TidligereSøknad
import no.nav.tiltakspenger.soknad.api.soknad.validering.barnetillegg
import no.nav.tiltakspenger.soknad.api.soknad.validering.spørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.soknad.validering.søknad
import no.nav.tiltakspenger.soknad.api.soknad.validering.tiltak
import no.nav.tiltakspenger.soknad.api.testutils.nyttTestFødselsnummer
import no.nav.tiltakspenger.soknad.api.util.genererMottattSøknadForTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

class SøknadPostgresRepoTest {
    private fun withCleanDb(test: (SøknadPostgresRepo) -> Unit) {
        testDatabaseManager.withMigratedDb(runIsolated = true) { dataSource ->
            test(SøknadPostgresRepo(dataSource))
        }
    }

    private fun withCleanDbAndDataSource(test: (SøknadPostgresRepo, DataSource) -> Unit) {
        testDatabaseManager.withMigratedDb(runIsolated = true) { dataSource ->
            test(SøknadPostgresRepo(dataSource), dataSource)
        }
    }

    @Nested
    inner class KanLagreSøknad {
        @Test
        fun `lagrer en helt vanlig søknad`() = withCleanDb { søknadRepo ->
            val nå = nå(fixedClock)
            val mottattSøknad = genererMottattSøknadForTest(
                opprettet = nå,
                eier = Applikasjonseier.Tiltakspenger,
            )
            søknadRepo.lagre(mottattSøknad)
            val actual = søknadRepo.hentSøknad(mottattSøknad.id)
            actual shouldBe mottattSøknad
        }

        @Test
        fun `kan lagre og hente en søknad som ikke har fnr i barnetillegg`() = withCleanDb { søknadRepo ->
            val nå = nå(fixedClock)
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
                                oppholdssted = null,
                            ),
                        ),
                    ),
                ),
            )
            søknadRepo.lagre(mottattSøknad)
            val actual = søknadRepo.hentSøknad(mottattSøknad.id)
            actual shouldBe mottattSøknad
        }

        @Test
        fun `kan lagre og hente en søknad som har fnr i barnetillegg`() = withCleanDb { søknadRepo ->
            val nå = nå(fixedClock)
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
                                oppholdInnenforEøs = null,
                                oppholdssted = Oppholdssted.IKKE_EØS_LAND,
                            ),
                        ),
                    ),
                ),
            )
            søknadRepo.lagre(mottattSøknad)
            val actual = søknadRepo.hentSøknad(mottattSøknad.id)
            actual shouldBe mottattSøknad
        }

        @Test
        fun `kan lagre og hente svaret på om søkeren søker barnetillegg`() = withCleanDb { søknadRepo ->
            listOf(true, false).forEach { søkerOmBarnetillegg ->
                val mottattSøknad = genererMottattSøknadForTest(
                    opprettet = nå(fixedClock),
                    eier = Applikasjonseier.Tiltakspenger,
                    søknadSpm = spørsmålsbesvarelser(
                        barnetillegg = barnetillegg(
                            søkerOmBarnetillegg = søkerOmBarnetillegg,
                            registrerteBarnSøktBarnetilleggFor = if (søkerOmBarnetillegg) {
                                listOf(
                                    RegistrertBarn(
                                        fnr = "potenti",
                                        fornavn = "quot",
                                        mellomnavn = null,
                                        etternavn = "conclusionemque",
                                        fødselsdato = LocalDate.now(fixedClock),
                                        oppholdInnenforEøs = null,
                                        oppholdssted = Oppholdssted.NORGE,
                                    ),
                                )
                            } else {
                                emptyList()
                            },
                        ),
                    ),
                )
                søknadRepo.lagre(mottattSøknad)
                val actual = søknadRepo.hentSøknad(mottattSøknad.id)
                actual shouldBe mottattSøknad
                actual!!.søknadSpm.barnetillegg.søkerOmBarnetillegg shouldBe søkerOmBarnetillegg
            }
        }
    }

    @Test
    fun `lagrer mottat søknad, journalfører, og sender til sbh-api`() = withCleanDb { søknadRepo ->
        val nå = nå(fixedClock)
        val søknad = søknad()
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = nå,
            eier = Applikasjonseier.Tiltakspenger,
        )
        søknadRepo.lagre(mottattSøknad)

        søknadRepo.hentSøknaderKlareForJournalføring().size shouldBe 0

        val soknaderUtenSaksnummer = søknadRepo.hentSøknaderUtenSaksnummer()
        soknaderUtenSaksnummer.size shouldBe 1

        // Oppdaterer med saksnummer
        val soknadMedSaksnummer = soknaderUtenSaksnummer.first().copy(
            saksnummer = "12345",
        )
        søknadRepo.oppdater(soknadMedSaksnummer)

        søknadRepo.hentSøknaderUtenSaksnummer().size shouldBe 0

        val søknaderSomIkkeErJounalført = søknadRepo.hentSøknaderKlareForJournalføring()
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
        søknadRepo.hentSøknaderKlareForJournalføring().size shouldBe 0

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
        val nå = nå(fixedClock)
        val søknad = søknad()
        val mottattSøknad = genererMottattSøknadForTest(
            opprettet = nå,
            eier = Applikasjonseier.Arena,
        )
        søknadRepo.lagre(mottattSøknad)

        søknadRepo.hentSøknaderUtenSaksnummer().size shouldBe 0

        val søknaderSomIkkeErJounalført = søknadRepo.hentSøknaderKlareForJournalføring()
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
        søknadRepo.hentSøknaderKlareForJournalføring().size shouldBe 0

        // sender søknaden til saksbehandling-api
        val søknaderSomIkkeErSendtTilSaksbehandlingApi = søknadRepo.hentSøknaderSomSkalSendesTilSaksbehandlingApi()
        søknaderSomIkkeErSendtTilSaksbehandlingApi.size shouldBe 0
    }

    @Test
    fun `hent brukers søknader`() = withCleanDb { søknadRepo ->
        val nå = nå(fixedClock)
        val fnr = nyttTestFødselsnummer()
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
            opprettet = nå(fixedClock),
            eier = Applikasjonseier.Tiltakspenger,
        ).copy(
            søknad = søknad(),
            fornavn = "fornavn",
            etternavn = "etternavn",
            journalført = nå(fixedClock),
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

    @Test
    fun `oppdaterFnr bytter fødselsnummer på brukerens søknader og lar andre være i fred`() = withCleanDb { søknadRepo ->
        val gammeltFnr = Fnr.random()
        val nyttFnr = Fnr.random()
        val urelatertFnr = Fnr.random()
        val søknad = genererMottattSøknadForTest(fnr = gammeltFnr.verdi, eier = Applikasjonseier.Tiltakspenger)
        val annenBrukersSøknad = genererMottattSøknadForTest(fnr = urelatertFnr.verdi, eier = Applikasjonseier.Tiltakspenger)
        søknadRepo.lagre(søknad)
        søknadRepo.lagre(annenBrukersSøknad)

        søknadRepo.oppdaterFnr(gammeltFnr = gammeltFnr, nyttFnr = nyttFnr)

        søknadRepo.hentSøknad(søknad.id)?.fnr shouldBe nyttFnr.verdi
        søknadRepo.hentSøknad(annenBrukersSøknad.id)?.fnr shouldBe urelatertFnr.verdi
    }

    @Nested
    inner class Duplikatsperre {
        private val januar = Periode(fra = LocalDate.of(2025, 1, 1), til = LocalDate.of(2025, 1, 31))
        private val svar = spørsmålsbesvarelser(tiltak = tiltak(aktivitetId = "aktivitet", periode = januar))

        private fun søknad(fnr: String, svar: SpørsmålsbesvarelserDTO = this.svar, eier: Applikasjonseier = Applikasjonseier.Tiltakspenger) =
            genererMottattSøknadForTest(fnr = fnr, eier = eier, søknadSpm = svar)

        @Test
        fun `henter tidligere søknader med svar for brukeren uansett eier`() = withCleanDb { søknadRepo ->
            val fnr = nyttTestFødselsnummer()
            val tiltakspengesøknad = søknad(fnr = fnr)
            val arenasøknad = søknad(fnr = fnr, svar = svar.copy(tiltak = tiltak(aktivitetId = "arena", periode = januar)), eier = Applikasjonseier.Arena)
            søknadRepo.lagre(tiltakspengesøknad)
            søknadRepo.lagre(arenasøknad)
            søknadRepo.lagre(søknad(fnr = nyttTestFødselsnummer()))

            søknadRepo.hentTidligereSøknader(fnr).sortedBy { it.aktivitetId } shouldBe listOf(
                TidligereSøknad(id = tiltakspengesøknad.id, mottatt = tiltakspengesøknad.opprettet, svar = tiltakspengesøknad.søknadSpm),
                TidligereSøknad(id = arenasøknad.id, mottatt = arenasøknad.opprettet, svar = arenasøknad.søknadSpm),
            )
        }

        @Test
        fun `lagrer ikke en søknad med samme tiltak, periode og svar som en tidligere søknad`() = withCleanDb { søknadRepo ->
            val fnr = nyttTestFødselsnummer()
            val første = søknad(fnr = fnr)
            søknadRepo.lagreHvisIkkeDuplikat(første) shouldBe Unit.right()

            val resultat = søknadRepo.lagreHvisIkkeDuplikat(søknad(fnr = fnr))

            resultat.leftOrNull()?.id shouldBe første.id
            søknadRepo.hentBrukersSøknader(fnr, Applikasjonseier.Tiltakspenger).map { it.id } shouldBe listOf(første.id)
        }

        @Test
        fun `lagrer søknad med endrede svar, for et annet tiltak, en forlenget periode og en annen bruker`() = withCleanDb { søknadRepo ->
            val fnr = nyttTestFødselsnummer()
            søknadRepo.lagreHvisIkkeDuplikat(søknad(fnr = fnr)) shouldBe Unit.right()

            søknadRepo.lagreHvisIkkeDuplikat(søknad(fnr = fnr, svar = svar.copy(mottarAndreUtbetalinger = !svar.mottarAndreUtbetalinger))) shouldBe Unit.right()
            søknadRepo.lagreHvisIkkeDuplikat(søknad(fnr = fnr, svar = svar.copy(tiltak = tiltak(aktivitetId = "annen", periode = januar)))) shouldBe Unit.right()
            søknadRepo.lagreHvisIkkeDuplikat(
                søknad(fnr = fnr, svar = svar.copy(tiltak = tiltak(aktivitetId = "aktivitet", periode = januar.copy(til = LocalDate.of(2025, 2, 28))))),
            ) shouldBe Unit.right()
            søknadRepo.lagreHvisIkkeDuplikat(søknad(fnr = nyttTestFødselsnummer())) shouldBe Unit.right()
        }

        /** Dobbeltklikk og parallelle faner: låsen per fnr sørger for at bare én av flere samtidige like innsendinger lagres. */
        @Test
        fun `bare én av flere samtidige innsendinger av samme søknad lagres`() = withCleanDb { søknadRepo ->
            val fnr = nyttTestFødselsnummer()
            val antall = 5
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(antall)
            try {
                val resultater = (1..antall)
                    .map {
                        executor.submit<Boolean> {
                            start.await()
                            søknadRepo.lagreHvisIkkeDuplikat(søknad(fnr = fnr)).isRight()
                        }
                    }
                    .also { start.countDown() }
                    .map { it.get(30, TimeUnit.SECONDS) }

                resultater.count { it } shouldBe 1
                søknadRepo.hentTidligereSøknader(fnr).size shouldBe 1
            } finally {
                executor.shutdownNow()
            }
        }
    }
}
