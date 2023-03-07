package no.nav.tiltakspenger.soknad.api.pdl

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mu.KotlinLogging
import no.nav.security.token.support.v2.TokenValidationContextPrincipal
import no.nav.tiltakspenger.soknad.api.PERSONALIA_PATH
import no.nav.tiltakspenger.soknad.api.auth.asTokenString
import no.nav.tiltakspenger.soknad.api.auth.getClaim
import no.nav.tiltakspenger.soknad.api.auth.oauth.ClientConfig
import no.nav.tiltakspenger.soknad.api.httpClientCIO

fun Route.pdlRoutes(config: ApplicationConfig) {
    val oauth2ClientTokenX = checkNotNull(ClientConfig(config, httpClientCIO()).clients["tokendings"])
    val oauth2ClientClientCredentials = checkNotNull(ClientConfig(config, httpClientCIO()).clients["azure"])

    val log = KotlinLogging.logger {}
    val secureLog = KotlinLogging.logger("tjenestekall")

    get(path = PERSONALIA_PATH) {
        val pdlUrl = config.property("endpoints.pdl").getString()
        val audience = config.property("audience.pdl").getString()
        val pid = call.getClaim("tokendings", "pid")
        val token = call.principal<TokenValidationContextPrincipal>().asTokenString()
        val tokenxResponse = oauth2ClientTokenX.tokenExchange(token, audience)
        val pdlTokenXClient = PdlClient(endpoint = pdlUrl, token = tokenxResponse.accessToken)
        pdlTokenXClient
            .fetchSøker(pid!!).onSuccess {
                try {
                    val person = it.toPerson()
                    val scope = config.property("scope.pdl").getString()
                    val clientCredentialsGrant = oauth2ClientClientCredentials.clientCredentials(scope)
                    val pdlCcrClient = PdlClient(endpoint = pdlUrl, token = clientCredentialsGrant.accessToken)
                    val forelderBarnRelasjon = person.forelderBarnRelasjon ?: emptyList()
                    val barn = forelderBarnRelasjon
                        .filter { it.relatertPersonsRolle == ForelderBarnRelasjonRolle.BARN }
                        .mapNotNull { it.relatertPersonsIdent }
                        .distinct()
                        .map { barnetsIdent ->
                            pdlCcrClient.fetchBarn(ident = barnetsIdent).getOrNull()?.toPerson()
                        }
                        .mapNotNull { it }
                    call.respond(person.toPersonDTO(barn))
                } catch (e: Exception) {
                    secureLog.error { e }
                    call.respondText(status = HttpStatusCode.InternalServerError, text = "Internal Server Error")
                }
            }.onFailure {
                secureLog.error { it }
                call.respondText(status = HttpStatusCode.InternalServerError, text = "Internal Server Error")
            }
    }
}