package no.nav.tiltakspenger.soknad.api.db

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
// TODO jah: Dette er ikke unikt for dette repoet og bør flyttes til libs med tester.
fun <T> String.hent(
    params: Map<String, Any> = emptyMap(),
    session: Session,
    rowMapping: (Row) -> T,
): T? {
    return session.run(queryOf(this, params).map { row -> rowMapping(row) }.asSingle)
}

fun <T> String.hentListe(
    params: Map<String, Any> = emptyMap(),
    session: Session,
    rowMapping: (Row) -> T,
): List<T> {
    return session.run(queryOf(this, params).map { row -> rowMapping(row) }.asList)
}

fun Row.booleanOrNull(name: String): Boolean? = this.anyOrNull(name)?.let { this.boolean(name) }
