package no.nav.tiltakspenger.soknad.api.testutils

import arrow.atomic.Atomic
import arrow.atomic.update
import arrow.core.Either
import arrow.core.right
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.tiltak.TiltakshistorikkDTO
import no.nav.tiltakspenger.soknad.api.tiltak.TiltakKlient
import java.time.Clock
import java.time.LocalDate

/**
 * Fake for [TiltakKlient], med ett tiltak søkeren kan velge.
 * Tiltaket starter innenfor vinduet ruta filtrerer på, slik at det faktisk vises.
 */
class TiltakKlientFake(
    private val clock: Clock,
    private val arrangør: String = "Lokal arrangør AS",
) : TiltakKlient {
    private val tiltak = Atomic(emptyMap<Fnr, List<TiltakshistorikkDTO>>())

    override suspend fun fetchTiltak(
        subjectToken: String,
        fnr: Fnr,
    ): Either<HttpKlientError, List<TiltakshistorikkDTO>> =
        (tiltak.get()[fnr] ?: listOf(standardTiltak())).right()

    /** Overstyrer tiltakene for én person; kall den før testen ber om tiltak. */
    fun leggTilTiltak(fnr: Fnr, tiltakshistorikk: List<TiltakshistorikkDTO>) {
        tiltak.update { it + (fnr to tiltakshistorikk) }
    }

    private fun standardTiltak() = tiltakshistorikk(
        arrangør = arrangør,
        deltakelseFom = LocalDate.now(clock).minusMonths(1),
    )
}
