package no.nav.tiltakspenger.soknad.api.tiltak

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.tiltak.TiltakshistorikkDTO

/**
 * Porten mot tiltakspenger-tiltak.
 * [TiltakspengerTiltakClient] er implementasjonen i drift; lokal kjøring bruker en fake.
 */
interface TiltakKlient {
    suspend fun fetchTiltak(subjectToken: String, fnr: Fnr): Either<HttpKlientError, List<TiltakshistorikkDTO>>
}
