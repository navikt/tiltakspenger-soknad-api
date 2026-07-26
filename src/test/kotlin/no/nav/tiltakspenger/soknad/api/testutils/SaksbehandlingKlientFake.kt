package no.nav.tiltakspenger.soknad.api.testutils

import arrow.atomic.Atomic
import arrow.atomic.update
import arrow.atomic.updateAndGet
import arrow.core.Either
import arrow.core.right
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.soknad.SøknadDTO
import no.nav.tiltakspenger.soknad.api.saksbehandlingApi.SaksbehandlingKlient

/**
 * Fake for [SaksbehandlingKlient], som tar imot søknader og deler ut saksnummer.
 * Saksnummeret er stabilt per person, slik at gjentatte kall for samme søker gir samme sak.
 */
class SaksbehandlingKlientFake : SaksbehandlingKlient {
    private val saksnumre = Atomic(emptyMap<Fnr, String>())
    private val oversendte = Atomic(emptyList<SøknadDTO>())
    val oversendteSøknader: List<SøknadDTO> get() = oversendte.get()

    override suspend fun sendSøknad(
        søknadDTO: SøknadDTO,
        correlationId: CorrelationId,
    ): Either<HttpKlientError, Unit> {
        oversendte.update { it + søknadDTO }
        return Unit.right()
    }

    override suspend fun hentEllerOpprettSaksnummer(
        fnr: Fnr,
        correlationId: CorrelationId,
    ): Either<HttpKlientError, String> =
        saksnumre.updateAndGet { tildelte ->
            if (tildelte.containsKey(fnr)) tildelte else tildelte + (fnr to "20250${(tildelte.size + 1).toString().padStart(4, '0')}")
        }.getValue(fnr).right()
}
