package no.nav.tiltakspenger.soknad.api.soknad

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.soknad.api.domain.toDbJson
import no.nav.tiltakspenger.soknad.api.domain.toSøknadDbJson
import no.nav.tiltakspenger.soknad.api.vedlegg.toDbJson
import no.nav.tiltakspenger.soknad.api.vedlegg.vedleggDbJson
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

class SøknadPostgresRepo(
    private val dataSource: DataSource,
) : SøknadRepo {
    override fun lagre(mottattSøknad: MottattSøknad) {
        sessionOf(dataSource).use {
            it.transaction { transaction ->
                transaction.run(
                    queryOf(
                        sqlLagre,
                        mapOf(
                            "id" to mottattSøknad.id.toString(),
                            "versjon" to mottattSøknad.versjon,
                            "soknad" to mottattSøknad.søknad?.toDbJson(),
                            "soknadSpm" to mottattSøknad.søknadSpm.toDbJson(),
                            "vedlegg" to mottattSøknad.vedlegg.toDbJson(),
                            "acr" to mottattSøknad.acr,
                            "fnr" to mottattSøknad.fnr,
                            "fornavn" to mottattSøknad.fornavn,
                            "etternavn" to mottattSøknad.etternavn,
                            "sendtTilVedtak" to mottattSøknad.sendtTilVedtak,
                            "journalfort" to mottattSøknad.journalført,
                            "journalpostId" to mottattSøknad.journalpostId?.toString(),
                            "opprettet" to mottattSøknad.opprettet,
                            "eier" to mottattSøknad.eier.toDb(),
                            "saksnummer" to mottattSøknad.saksnummer,
                        ),
                    ).asUpdate,
                )
            }
        }
    }

    override fun oppdater(mottattSøknad: MottattSøknad) {
        sessionOf(dataSource).use {
            it.transaction { transaction ->
                transaction.run(
                    queryOf(
                        sqlOppdater,
                        mapOf(
                            "id" to mottattSøknad.id.toString(),
                            "soknad" to mottattSøknad.søknad?.toDbJson(),
                            "fornavn" to mottattSøknad.fornavn,
                            "etternavn" to mottattSøknad.etternavn,
                            "sendtTilVedtak" to mottattSøknad.sendtTilVedtak,
                            "journalfort" to mottattSøknad.journalført,
                            "journalpostId" to mottattSøknad.journalpostId?.toString(),
                            "saksnummer" to mottattSøknad.saksnummer,
                        ),
                    ).asUpdate,
                )
            }
        }
    }

    override fun hentSøknaderUtenSaksnummer(): List<MottattSøknad> {
        return sessionOf(dataSource).use {
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
                        row.toMottattSøknad()
                    }.asList,
                )
            }
        }
    }

    override fun hentSøknaderKlareForJournalføring(): List<MottattSøknad> {
        return sessionOf(dataSource).use {
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
                        row.toMottattSøknad()
                    }.asList,
                )
            }
        }
    }

    override fun hentSøknaderSomSkalSendesTilSaksbehandlingApi(): List<MottattSøknad> {
        return sessionOf(dataSource).use {
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
                        row.toMottattSøknad()
                    }.asList,
                )
            }
        }
    }

    override fun hentBrukersSøknader(fnr: String, eier: Applikasjonseier): List<MottattSøknad> {
        return sessionOf(dataSource).use {
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
                        row.toMottattSøknad()
                    }.asList,
                )
            }
        }
    }

    override fun hentSøknad(søknadId: SøknadId): MottattSøknad? {
        return sessionOf(dataSource).use {
            it.transaction { transaction ->
                transaction.run(
                    queryOf(
                        """
                           select * from søknad where id = :id
                        """.trimIndent(),
                        mapOf(
                            "id" to søknadId.toString(),
                        ),
                    ).map { row ->
                        row.toMottattSøknad()
                    }.asSingle,
                )
            }
        }
    }

    override fun oppdaterFnr(gammeltFnr: Fnr, nyttFnr: Fnr) {
        sessionOf(dataSource).use {
            it.transaction { transaction ->
                transaction.run(
                    queryOf(
                        """update søknad set fnr = :nytt_fnr where fnr = :gammelt_fnr""",
                        mapOf(
                            "nytt_fnr" to nyttFnr.verdi,
                            "gammelt_fnr" to gammeltFnr.verdi,
                        ),
                    ).asUpdate,
                )
            }
        }
    }

    private fun Row.toMottattSøknad(): MottattSøknad {
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
            journalpostId = stringOrNull("journalpostId")?.let(::JournalpostId),
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
