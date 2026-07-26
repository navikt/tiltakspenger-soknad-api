package no.nav.tiltakspenger.soknad.api.tiltak

import arrow.core.Either
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.loggFeil
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import java.time.Clock

class TiltakService(
    private val tiltakspengerTiltakClient: TiltakspengerTiltakClient,
    private val clock: Clock,
    private val sikkerlogg: Sikkerlogg,
) {
    private val log = KotlinLogging.logger {}

    suspend fun hentTiltak(
        subjectToken: String,
        fnr: Fnr,
        maskerArrangørnavn: Boolean,
    ): Either<HttpKlientError, List<TiltaksdeltakelseDto>> =
        tiltakspengerTiltakClient.fetchTiltak(subjectToken = subjectToken, fnr = fnr)
            .onLeft { it.loggFeil(log, "henting av tiltak fra tiltakspenger-tiltak", "", sikkerlogg) }
            .map { tiltak ->
                tiltak.toTiltakDto(maskerArrangørnavn).filter { it.erInnenforRelevantTidsrom(clock) }
            }
}
