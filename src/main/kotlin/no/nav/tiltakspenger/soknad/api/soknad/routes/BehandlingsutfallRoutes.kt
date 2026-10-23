package no.nav.tiltakspenger.soknad.api.soknad.routes

import arrow.core.Either
import arrow.core.getOrElse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.libs.texas.TexasPrincipalInternal
import no.nav.tiltakspenger.libs.texas.isSystembruker
import no.nav.tiltakspenger.soknad.api.SAKSBEHANDLING_PATH
import no.nav.tiltakspenger.soknad.api.soknad.Behandlingsresultat
import no.nav.tiltakspenger.soknad.api.soknad.Behandlingsutfall
import no.nav.tiltakspenger.soknad.api.soknad.BehandlingsutfallService
import no.nav.tiltakspenger.soknad.api.soknad.SøknadFinnesIkke
import java.time.LocalDateTime

/**
 * Tar imot utfallet av en søknadsbehandling fra saksbehandling-api når vedtaket er iverksatt.
 * Skal ligge bak Azure AD-autentisering; nais-manifestet slipper bare saksbehandling-api inn.
 * Bare systemtokens slipper gjennom, ikke tokens for innloggede ansatte.
 *
 * Svarer 403 når tokenet ikke er et systemtoken.
 * Svarer 204 når utfallet er lagret, også når et nyere utfall allerede var lagret.
 * Svarer 404 når søknaden ikke finnes hos oss; saksbehandling-api prøver da ikke igjen.
 * Svarer 400 når forespørselen ikke kan tolkes.
 */
fun Route.behandlingsutfallRoutes(
    behandlingsutfallService: BehandlingsutfallService,
) {
    val log = KotlinLogging.logger { }
    post("$SAKSBEHANDLING_PATH/soknad/utfall") {
        val principal = call.principal<TexasPrincipalInternal>() ?: throw IllegalStateException("Mangler principal")
        if (!isSystembruker(principal.claims)) {
            log.warn { "Avviste behandlingsutfall fra et token som ikke er et systemtoken. azp_name: ${principal.claims["azp_name"]}." }
            call.respond(HttpStatusCode.Forbidden)
            return@post
        }
        val (søknadId, behandlingsutfall) = Either.catch {
            deserialize<BehandlingsutfallRequest>(call.receiveText()).tilDomene()
        }.getOrElse {
            log.warn(it) { "Kunne ikke tolke behandlingsutfall fra saksbehandling-api." }
            call.respond(HttpStatusCode.BadRequest, "Kunne ikke tolke behandlingsutfallet")
            return@post
        }
        behandlingsutfallService.lagreBehandlingsutfall(søknadId, behandlingsutfall).fold(
            {
                when (it) {
                    SøknadFinnesIkke -> call.respond(HttpStatusCode.NotFound, "Fant ikke søknad $søknadId")
                }
            },
            { call.respond(HttpStatusCode.NoContent) },
        )
    }
}

private data class BehandlingsutfallRequest(
    val søknadId: String,
    val vedtakId: String,
    val utfall: UtfallDto,
    val vedtakstidspunkt: LocalDateTime,
) {
    enum class UtfallDto {
        INNVILGELSE,
        AVSLAG,
    }

    fun tilDomene(): Pair<SøknadId, Behandlingsutfall> =
        SøknadId.fromString(søknadId) to Behandlingsutfall(
            resultat = when (utfall) {
                UtfallDto.INNVILGELSE -> Behandlingsresultat.INNVILGELSE
                UtfallDto.AVSLAG -> Behandlingsresultat.AVSLAG
            },
            vedtakId = vedtakId,
            vedtakstidspunkt = vedtakstidspunkt,
        )
}
