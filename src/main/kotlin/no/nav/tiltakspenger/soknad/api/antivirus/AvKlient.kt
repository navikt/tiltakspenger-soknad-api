package no.nav.tiltakspenger.soknad.api.antivirus

import arrow.core.Either
import arrow.core.Nel
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

/**
 * Porten mot virussjekken.
 * [ClamAvClient] er implementasjonen i drift; lokal kjøring bruker en fake.
 */
interface AvKlient {
    suspend fun scan(vedleggsListe: Nel<Vedlegg>): Either<HttpKlientError, List<AvSjekkResultat>>
}
