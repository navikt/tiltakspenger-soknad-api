package no.nav.tiltakspenger.soknad.api.auth.texas

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.response.respond
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.soknad.api.auth.texas.client.TexasClient

val tillatteInnloggingsnivaer = listOf("idporten-loa-high", "Level4")
val log = KotlinLogging.logger("TexasAuth")

class TexasAuthenticationProvider(
    config: Config,
) : AuthenticationProvider(config) {
    class Config internal constructor(
        name: String?,
        val texasClient: TexasClient,
    ) : AuthenticationProvider.Config(name)

    private val texasClient = config.texasClient

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val applicationCall = context.call
        val token =
            (applicationCall.request.parseAuthorizationHeader() as? HttpAuthHeader.Single)
                ?.takeIf { header -> header.authScheme.lowercase() == AuthScheme.Bearer.lowercase() }
                ?.blob

        if (token == null) {
            log.warn { "unauthenticated: no Bearer token found in Authorization header" }
            context.loginChallenge(AuthenticationFailedCause.NoCredentials)
            return
        }

        val introspectResponse =
            try {
                texasClient.introspectToken(token)
            } catch (e: Exception) {
                log.error { "unauthenticated: introspect request failed: ${e.message}" }
                context.loginChallenge(AuthenticationFailedCause.Error(e.message ?: "introspect request failed"))
                return
            }

        if (!introspectResponse.active) {
            log.warn { "unauthenticated: ${introspectResponse.error}" }
            context.loginChallenge(AuthenticationFailedCause.InvalidCredentials)
            return
        }

        val tokenClaims = introspectResponse.other

        val fnrString = tokenClaims["pid"]?.toString()
        if (fnrString == null) {
            log.warn { "Fant ikke fnr i pid-claim" }
            context.call.respond(HttpStatusCode.InternalServerError)
            return
        }
        val fnr = Fnr.fromString(fnrString)

        val level = tokenClaims["acr"]?.toString()
        if (level == null || level !in tillatteInnloggingsnivaer) {
            log.warn { "unauthenticated: må ha innloggingsnivå 4" }
            context.call.respond(HttpStatusCode.Unauthorized)
            return
        }
        context.principal(
            TexasPrincipal(
                claims = introspectResponse.other,
                token = token,
                fnr = fnr,
                acr = level,
            ),
        )
    }

    private fun AuthenticationContext.loginChallenge(cause: AuthenticationFailedCause) {
        challenge("Texas", cause) { authenticationProcedureChallenge, call ->
            call.respond(HttpStatusCode.Unauthorized)
            authenticationProcedureChallenge.complete()
        }
    }
}

data class TexasPrincipal(
    val claims: Map<String, Any?>,
    val token: String,
    val fnr: Fnr,
    val acr: String,
)
