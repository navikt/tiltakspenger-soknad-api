package no.nav.tiltakspenger.soknad.api.tiltak

import arrow.core.Either
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.loggFeil
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.soknad.api.tiltak.skygge.TiltaksdeltakelseSkygge
import java.time.Clock

class TiltakService(
    private val tiltakKlient: TiltakKlient,
    private val clock: Clock,
    private val sikkerlogg: Sikkerlogg,
    private val tiltaksdeltakelseSkygge: TiltaksdeltakelseSkygge,
) {
    private val log = KotlinLogging.logger {}

    suspend fun hentTiltak(
        subjectToken: String,
        fnr: Fnr,
        maskerArrangørnavn: Boolean,
        correlationId: CorrelationId,
    ): Either<HttpKlientError, List<TiltaksdeltakelseDto>> =
        tiltakKlient.fetchTiltak(subjectToken = subjectToken, fnr = fnr)
            .onLeft { it.loggFeil(log, "henting av tiltak fra tiltakspenger-tiltak", "", sikkerlogg) }
            .onRight { svar ->
                // Kun ferske svar skygges: et cachet svar kan være opptil en time gammelt, og da hadde skyggen målt tid i stedet for mapping.
                if (!svar.fraCache) {
                    tiltaksdeltakelseSkygge.kjørISkyggen(fnr = fnr, gammel = svar.deltakelser, correlationId = correlationId)
                }
            }
            .map { svar ->
                svar.deltakelser.toTiltakDto(maskerArrangørnavn).filter { it.erInnenforRelevantTidsrom(clock) }
            }
}
