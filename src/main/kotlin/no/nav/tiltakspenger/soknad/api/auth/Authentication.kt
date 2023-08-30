package no.nav.tiltakspenger.soknad.api.auth

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authentication
import no.nav.security.token.support.v2.RequiredClaims
import no.nav.security.token.support.v2.TokenValidationContextPrincipal
import no.nav.security.token.support.v2.asIssuerProps
import no.nav.security.token.support.v2.tokenValidationSupport

fun Application.installAuthentication() {
    val config = environment.config
    install(Authentication) {
        val issuers = config.asIssuerProps().keys
        issuers.forEach { issuer: String ->
            tokenValidationSupport(
                name = issuer,
                config = config,
                requiredClaims = RequiredClaims(
                    issuer = issuer,
                    claimMap = arrayOf("acr=idporten-loa-high"),
                    combineWithOr = false,
                ),
            )
        }
    }
}

internal fun ApplicationCall.getClaim(issuer: String, name: String): String? =
    this.authentication.principal<TokenValidationContextPrincipal>()
        ?.context
        ?.getClaims(issuer)
        ?.getStringClaim(name)

internal fun TokenValidationContextPrincipal?.asTokenString(): String =
    this?.context?.firstValidToken?.map { it.tokenAsString }?.orElse(null)
        ?: throw RuntimeException("no token found in call context")
