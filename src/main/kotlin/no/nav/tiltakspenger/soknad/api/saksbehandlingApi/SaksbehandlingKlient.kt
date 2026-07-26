package no.nav.tiltakspenger.soknad.api.saksbehandlingApi

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.soknad.SøknadDTO

/**
 * Porten mot tiltakspenger-saksbehandling-api.
 * [SaksbehandlingApiKlient] er implementasjonen i drift; lokal kjøring bruker en fake.
 */
interface SaksbehandlingKlient {
    suspend fun sendSøknad(
        søknadDTO: SøknadDTO,
        correlationId: CorrelationId,
    ): Either<HttpKlientError, Unit>

    suspend fun hentEllerOpprettSaksnummer(
        fnr: Fnr,
        correlationId: CorrelationId,
    ): Either<HttpKlientError, String>
}
