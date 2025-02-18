package no.nav.tiltakspenger.soknad.api.soknad

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.soknad.api.db.DataSource
import no.nav.tiltakspenger.soknad.api.domain.toDbJson
import no.nav.tiltakspenger.soknad.api.domain.toSøknadDbJson
import no.nav.tiltakspenger.soknad.api.vedlegg.toDbJson
import no.nav.tiltakspenger.soknad.api.vedlegg.vedleggDbJson
import org.intellij.lang.annotations.Language

class SøknadRepo {
    fun lagre(dto: MottattSøknad) {
        sessionOf(DataSource.hikariDataSource).use {
            it.transaction { transaction ->
                transaction.run(
                    queryOf(
                        sqlLagre,
                        mapOf(
                            "id" to dto.id.toString(),
                            "versjon" to dto.versjon,
                            "soknad" to dto.søknad?.toDbJson(),
                            "soknadSpm" to dto.søknadSpm.toDbJson(),
                            "vedlegg" to dto.vedlegg.toDbJson(),
                            "acr" to dto.acr,
                            "fnr" to dto.fnr,
                            "fornavn" to dto.fornavn,
                            "etternavn" to dto.etternavn,
                            "sendtTilVedtak" to dto.sendtTilVedtak,
                            "journalfort" to dto.journalført,
                            "journalpostId" to dto.journalpostId,
                            "opprettet" to dto.opprettet,
                            "eier" to dto.eier.toDb(),
                            "saksnummer" to dto.saksnummer,
                        ),
                    ).asUpdate,
                )
            }
        }
    }

    fun oppdater(dto: MottattSøknad) {
        sessionOf(DataSource.hikariDataSource).use {
            it.transaction { transaction ->
                transaction.run(
                    queryOf(
                        sqlOppdater,
                        mapOf(
                            "id" to dto.id.toString(),
                            "soknad" to dto.søknad?.toDbJson(),
                            "fornavn" to dto.fornavn,
                            "etternavn" to dto.etternavn,
                            "sendtTilVedtak" to dto.sendtTilVedtak,
                            "journalfort" to dto.journalført,
                            "journalpostId" to dto.journalpostId,
                            "saksnummer" to dto.saksnummer,
                        ),
                    ).asUpdate,
                )
            }
        }
    }

    fun hentSoknaderUtenSaksnummer(): List<MottattSøknad> {
        return sessionOf(DataSource.hikariDataSource).use {
            it.transaction { transaction ->
                transaction.run(
                    queryOf(
                        //language=SQL
                        """
                           select * from søknad 
                             where saksnummer is null
                             and eier = :tp
                        """.trimIndent(),
                        mapOf(
                            "tp" to Applikasjonseier.Tiltakspenger.toDb(),
                        ),
                    ).map { row ->
                        row.toSøknadDbDto()
                    }.asList,
                )
            }
        }
    }

    fun hentAlleSøknadDbDtoSomIkkeErJournalført(): List<MottattSøknad> {
        return sessionOf(DataSource.hikariDataSource).use {
            it.transaction { transaction ->
                transaction.run(
                    queryOf(
                        //language=SQL
                        """
                            select * from søknad 
                            where journalført is null
                            and (saksnummer is not null or eier = :arena)
                        """.trimIndent(),
                        mapOf(
                            "arena" to Applikasjonseier.Arena.toDb(),
                        ),
                    ).map { row ->
                        row.toSøknadDbDto()
                    }.asList,
                )
            }
        }
    }

    fun hentSøknaderSomSkalSendesTilSaksbehandlingApi(): List<MottattSøknad> {
        return sessionOf(DataSource.hikariDataSource).use {
            it.transaction { transaction ->
                transaction.run(
                    queryOf(
                        //language=SQL
                        """
                           select * from søknad 
                             where journalført is not null 
                             and sendt_til_vedtak is null
                             and eier = :tp
                        """.trimIndent(),
                        mapOf(
                            "tp" to Applikasjonseier.Tiltakspenger.toDb(),
                        ),
                    ).map { row ->
                        row.toSøknadDbDto()
                    }.asList,
                )
            }
        }
    }

    fun hentBrukersSøknader(fnr: String, eier: Applikasjonseier): List<MottattSøknad> {
        return sessionOf(DataSource.hikariDataSource).use {
            it.transaction { transaction ->
                transaction.run(
                    queryOf(
                        //language=SQL
                        """
                           select * from søknad 
                             where fnr = :fnr 
                             and eier = :eier
                        """.trimIndent(),
                        mapOf(
                            "fnr" to fnr,
                            "eier" to eier.toDb(),
                        ),
                    ).map { row ->
                        row.toSøknadDbDto()
                    }.asList,
                )
            }
        }
    }

    fun hentSoknad(soknadId: SøknadId): MottattSøknad? {
        return sessionOf(DataSource.hikariDataSource).use {
            it.transaction { transaction ->
                transaction.run(
                    queryOf(
                        """
                           select * from søknad where id = :id
                        """.trimIndent(),
                        mapOf(
                            "id" to soknadId.toString(),
                        ),
                    ).map { row ->
                        row.toSøknadDbDto()
                    }.asSingle,
                )
            }
        }
    }

    private fun Row.toSøknadDbDto(): MottattSøknad {
        return MottattSøknad(
            id = SøknadId.fromString(string("id")),
            versjon = string("versjon"),
            søknad = stringOrNull("søknad")?.toSøknadDbJson(),
            søknadSpm = string("søknadSpm").toSpørsmålsbesvarelserDbJson(),
            vedlegg = string("vedlegg").vedleggDbJson(),
            acr = string("acr"),
            fnr = string("fnr"),
            fornavn = stringOrNull("fornavn"),
            etternavn = stringOrNull("etternavn"),
            sendtTilVedtak = localDateTimeOrNull("sendt_til_vedtak"),
            journalført = localDateTimeOrNull("journalført"),
            journalpostId = stringOrNull("journalpostId"),
            opprettet = localDateTime("opprettet"),
            eier = Applikasjonseier.toApplikasjonseier(string("eier")),
            saksnummer = stringOrNull("saksnummer"),
        )
    }

    @Language("PostgreSQL")
    private val sqlLagre =
        """
        insert into søknad (
            id,
            versjon,
            søknad,
            søknadSpm,
            vedlegg,
            acr,
            fnr,
            fornavn,
            etternavn,
            sendt_til_vedtak,
            journalført,
            journalpostId,
            opprettet,
            eier,
            saksnummer
        ) values (
            :id,
            :versjon,
            to_jsonb(:soknad::jsonb),
            to_jsonb(:soknadSpm::jsonb),
            to_jsonb(:vedlegg::jsonb),
            :acr,
            :fnr,
            :fornavn,
            :etternavn,
            :sendtTilVedtak,
            :journalfort,
            :journalpostId,
            :opprettet,
            :eier,
            :saksnummer
        )
        """.trimIndent()

    @Language("PostgreSQL")
    private val sqlOppdater =
        """
            update søknad set
                fornavn = :fornavn,
                etternavn = :etternavn,
                søknad = to_jsonb(:soknad::jsonb),
                sendt_til_vedtak = :sendtTilVedtak,
                journalført = :journalfort,
                journalpostId = :journalpostId,
                saksnummer = :saksnummer
            where id = :id
        """.trimIndent()
}
