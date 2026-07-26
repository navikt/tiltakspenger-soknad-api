package no.nav.tiltakspenger.soknad.api.dokarkiv

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.JournalpostId

/**
 * Porten mot dokarkiv (joark).
 * [DokarkivClient] er implementasjonen i drift; lokal kjøring bruker en fake.
 */
interface JournalpostKlient {
    suspend fun opprettJournalpost(
        request: JournalpostRequest,
        callId: String,
    ): Either<KunneIkkeJournalføre, JournalpostId>
}
