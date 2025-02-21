package no.nav.tiltakspenger.soknad.api.auth.texas.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.logging.sikkerlogg
import no.nav.tiltakspenger.soknad.api.auth.texas.log
import no.nav.tiltakspenger.soknad.api.httpClientCIO

class TexasClient(
    private val introspectionUrl: String,
    private val tokenUrl: String,
    private val tokenExchangeUrl: String,
    private val httpClient: HttpClient = httpClientCIO(),
) {
    suspend fun introspectToken(token: String, identityProvider: String = "tokenx"): TexasIntrospectionResponse {
        val texasIntrospectionRequest = TexasIntrospectionRequest(
            identityProvider = identityProvider,
            token = token,
        )
        try {
            val response =
                httpClient.post(introspectionUrl) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(texasIntrospectionRequest)
                }
            return response.body<TexasIntrospectionResponse>()
        } catch (e: Exception) {
            if (e is ResponseException) {
                log.error { "Kall for autentisering mot Texas feilet, responskode ${e.response.status}" }
            }
            log.error { "Kall for autentisering mot Texas feilet, se sikker logg for detaljer" }
            sikkerlogg.error(e) { "Kall for autentisering mot Texas feilet, melding: ${e.message}" }
            throw e
        }
    }

    suspend fun getSystemToken(audienceTarget: String, identityProvider: String = "azuread"): AccessToken {
        val texasTokenRequest = TexasTokenRequest(
            identityProvider = identityProvider,
            target = audienceTarget,
        )
        try {
            val response =
                httpClient.post(tokenUrl) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(texasTokenRequest)
                }
            val texasTokenResponse = response.body<TexasTokenResponse>()
            return texasTokenResponse.toAccessToken()
        } catch (e: Exception) {
            if (e is ResponseException) {
                log.error { "Kall for å hente token mot Texas feilet, responskode ${e.response.status}" }
            }
            log.error { "Kall å hente token mot Texas feilet, se sikker logg for detaljer" }
            sikkerlogg.error(e) { "Kall å hente token mot Texas feilet, melding: ${e.message}" }
            throw e
        }
    }

    suspend fun exchangeToken(userToken: String, audienceTarget: String, identityProvider: String = "tokenx"): AccessToken {
        val texasExchangeTokenRequest = TexasExchangeTokenRequest(
            identityProvider = identityProvider,
            target = audienceTarget,
            userToken = userToken,
        )
        try {
            val response =
                httpClient.post(tokenExchangeUrl) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(texasExchangeTokenRequest)
                }
            val texasTokenResponse = response.body<TexasTokenResponse>()
            return texasTokenResponse.toAccessToken()
        } catch (e: Exception) {
            if (e is ResponseException) {
                log.error { "Kall for å veksle token mot Texas feilet, responskode ${e.response.status}" }
            }
            log.error { "Kall å veksle token mot Texas feilet, se sikker logg for detaljer" }
            sikkerlogg.error(e) { "Kall å veksle token mot Texas feilet, melding: ${e.message}" }
            throw e
        }
    }
}
