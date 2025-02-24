package no.nav.tiltakspenger.soknad.api.auth.texas

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.authorization
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import mu.KotlinLogging
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.soknad.api.auth.texas.client.TexasClient

class AuthPluginConfiguration(
    var client: TexasClient? = null,
)

val log = KotlinLogging.logger("TexasAuth")

val tillatteInnloggingsnivaer = listOf("idporten-loa-high", "Level4")

private val fnrAttributeKey = AttributeKey<Fnr>("fnr")
private val tokenAttributeKey = AttributeKey<String>("token")
private val acrAttributeKey = AttributeKey<String>("acr")

val TexasAuth =
    createRouteScopedPlugin(
        name = "TexasAuth",
        createConfiguration = ::AuthPluginConfiguration,
    ) {
        val client = pluginConfig.client ?: throw IllegalArgumentException("TexasAuth plugin: client must be set")

        pluginConfig.apply {
            onCall { call ->
                val token = call.bearerToken()
                if (token == null) {
                    log.warn { "unauthenticated: no Bearer token found in Authorization header" }
                    call.respond(HttpStatusCode.Unauthorized)
                    return@onCall
                }

                val introspectResponse =
                    try {
                        client.introspectToken(token = token)
                    } catch (e: Exception) {
                        log.error { "unauthenticated: introspect request failed: ${e.message}" }
                        call.respond(HttpStatusCode.Unauthorized)
                        return@onCall
                    }

                if (!introspectResponse.active) {
                    log.warn { "unauthenticated: ${introspectResponse.error}" }
                    call.respond(HttpStatusCode.Unauthorized)
                    return@onCall
                }

                val tokenClaims = introspectResponse.other

                val fnrString = tokenClaims["pid"]?.toString()
                if (fnrString == null) {
                    log.warn { "Fant ikke fnr i pid-claim" }
                    call.respond(HttpStatusCode.InternalServerError)
                    return@onCall
                }
                val fnr = Fnr.fromString(fnrString)

                val level = tokenClaims["acr"]?.toString()
                if (level == null || level !in tillatteInnloggingsnivaer) {
                    log.warn { "unauthenticated: må ha innloggingsnivå 4" }
                    call.respond(HttpStatusCode.Unauthorized)
                    return@onCall
                }

                call.attributes.put(fnrAttributeKey, fnr)
                call.attributes.put(tokenAttributeKey, token)
                call.attributes.put(acrAttributeKey, level)
            }
        }
    }

fun ApplicationCall.bearerToken(): String? =
    request
        .authorization()
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.removePrefix("Bearer ")
        ?.removePrefix("bearer ")

fun ApplicationCall.fnr(): Fnr {
    return this.attributes[fnrAttributeKey]
}

fun ApplicationCall.token(): String {
    return this.attributes[tokenAttributeKey]
}

fun ApplicationCall.acr(): String {
    return this.attributes[acrAttributeKey]
}
