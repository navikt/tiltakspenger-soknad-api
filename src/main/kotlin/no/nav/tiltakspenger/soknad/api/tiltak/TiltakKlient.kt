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
    suspend fun fetchTiltak(subjectToken: String, fnr: Fnr): Either<HttpKlientError, TiltakshistorikkSvar>
}

/**
 * Tiltaksdeltakelsene fra tiltakspenger-tiltak, med opplysning om hvor svaret kom fra.
 *
 * [fraCache] finnes kun så lenge skyggekjøringen mot tiltaksdeltakelse-modulen står på, og dør med den.
 * Et cachet svar kan være opptil en time gammelt, og et slikt svar skal ikke sammenlignes med et ferskt svar fra ny vei — da måler skyggen tid i stedet for mapping, og et avvik som skyldes alderen er ikke til å skille fra en ekte feil.
 */
data class TiltakshistorikkSvar(
    val deltakelser: List<TiltakshistorikkDTO>,
    val fraCache: Boolean,
)
