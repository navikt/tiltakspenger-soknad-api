package no.nav.tiltakspenger.soknad.api.pdl.client

import arrow.core.Either
import arrow.core.Nel
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkerRespons
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkersBarnRespons

/**
 * Porten mot PDL.
 * [PdlClient] er implementasjonen i drift; lokal kjøring bruker en fake.
 */
interface PersonKlient {
    suspend fun fetchBarn(identer: Nel<String>): Either<KanIkkeHentePerson, SøkersBarnRespons>

    suspend fun fetchSøker(
        fødselsnummer: String,
        subjectToken: String,
    ): Either<KanIkkeHentePerson, SøkerRespons>

    suspend fun fetchSøkerSystembruker(fødselsnummer: String): Either<KanIkkeHentePerson, SøkerRespons>
}
