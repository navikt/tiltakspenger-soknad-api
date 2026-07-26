package no.nav.tiltakspenger.soknad.api.testutils

import arrow.atomic.Atomic
import arrow.core.Either
import arrow.core.right
import no.nav.tiltakspenger.libs.common.JournalpostId
import no.nav.tiltakspenger.soknad.api.dokarkiv.JournalpostKlient
import no.nav.tiltakspenger.soknad.api.dokarkiv.JournalpostRequest
import no.nav.tiltakspenger.soknad.api.dokarkiv.KunneIkkeJournalføre

/**
 * Fake for [JournalpostKlient], som kvitterer med en ny journalpost-ID per kall.
 * ID-ene er fortløpende og ikke tilfeldige, slik at et lokalt kjøremønster kan gjentas.
 */
class JournalpostKlientFake : JournalpostKlient {
    private val opprettede = Atomic(emptyList<Pair<JournalpostId, JournalpostRequest>>())
    val opprettedeJournalposter: List<Pair<JournalpostId, JournalpostRequest>> get() = opprettede.get()

    override suspend fun opprettJournalpost(
        request: JournalpostRequest,
        callId: String,
    ): Either<KunneIkkeJournalføre, JournalpostId> {
        // ID-en utledes inni oppdateringen, slik at nummereringen holder også når flere kall kommer samtidig.
        return opprettede.updateAndGet { opprettet ->
            opprettet + (JournalpostId("4${(opprettet.size + 1).toString().padStart(8, '0')}") to request)
        }.last().first.right()
    }
}
