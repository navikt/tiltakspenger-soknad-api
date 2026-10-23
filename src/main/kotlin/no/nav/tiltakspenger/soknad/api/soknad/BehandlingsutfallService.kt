package no.nav.tiltakspenger.soknad.api.soknad

import arrow.core.Either
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.common.SøknadId

/**
 * Tar imot utfallet av behandlingen av en søknad fra saksbehandling-api.
 * En søknad med utfall er ferdig behandlet, og bruker kan da sende den samme søknaden på nytt (se [duplikatAv]).
 */
class BehandlingsutfallService(
    private val søknadRepo: SøknadRepo,
) {
    private val log = KotlinLogging.logger {}

    fun lagreBehandlingsutfall(søknadId: SøknadId, behandlingsutfall: Behandlingsutfall): Either<SøknadFinnesIkke, Unit> =
        søknadRepo.lagreBehandlingsutfall(søknadId, behandlingsutfall)
            .onLeft {
                log.warn { "Fikk behandlingsutfall for en søknad vi ikke har. SøknadId: $søknadId. VedtakId: ${behandlingsutfall.vedtakId}." }
            }
            .onRight {
                log.info { "Lagret behandlingsutfall ${behandlingsutfall.resultat} for søknad $søknadId. VedtakId: ${behandlingsutfall.vedtakId}." }
            }
}
