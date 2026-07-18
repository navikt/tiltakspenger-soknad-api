package no.nav.tiltakspenger.soknad.api.tiltak

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.common.Fnr
import java.time.Clock

class TiltakService(
    private val tiltakspengerTiltakClient: TiltakspengerTiltakClient,
    private val clock: Clock,
) {
    private val log = KotlinLogging.logger {}

    suspend fun hentTiltak(subjectToken: String, fnr: Fnr, maskerArrangørnavn: Boolean): List<TiltaksdeltakelseDto> {
        log.info { "Henter tiltak" }
        val result = tiltakspengerTiltakClient.fetchTiltak(
            subjectToken = subjectToken,
            fnr = fnr,
        )
        if (result.isSuccess) {
            log.info { "Henting av tiltak OK" }
            val tiltak = result.getOrNull()
            if (tiltak !== null) {
                return tiltak.toTiltakDto(maskerArrangørnavn).filter {
                    it.erInnenforRelevantTidsrom(clock)
                }
            }
        }
        log.error { "Noe gikk galt under kall til tiltakspenger-tiltak" }
        throw IllegalStateException("Noe gikk galt under kall til tiltakspenger-tiltak", result.exceptionOrNull())
    }
}
