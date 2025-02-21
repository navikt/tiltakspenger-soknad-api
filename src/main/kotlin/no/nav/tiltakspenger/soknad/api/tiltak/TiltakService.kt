package no.nav.tiltakspenger.soknad.api.tiltak

import mu.KotlinLogging

class TiltakService(
    private val tiltakspengerTiltakClient: TiltakspengerTiltakClient,
) {
    private val log = KotlinLogging.logger {}

    suspend fun hentTiltak(subjectToken: String, maskerArrangørnavn: Boolean): List<TiltaksdeltakelseDto> {
        log.info { "Henter tiltak" }
        val result = tiltakspengerTiltakClient.fetchTiltak(subjectToken = subjectToken)
        if (result.isSuccess) {
            log.info { "Henting av tiltak OK" }
            val tiltak = result.getOrNull()
            if (tiltak !== null) {
                return tiltak.toTiltakDto(maskerArrangørnavn).filter {
                    it.erInnenforRelevantTidsrom()
                }
            }
        }
        log.error { "Noe gikk galt under kall til tiltakspenger-tiltak" }
        throw IllegalStateException("Noe gikk galt under kall til tiltakspenger-tiltak", result.exceptionOrNull())
    }
}
