package no.nav.tiltakspenger.soknad.api.auth.texas.client

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.tiltakspenger.libs.common.AccessToken
import java.time.Instant

data class TexasTokenRequest(
    @JsonProperty("identity_provider") val identityProvider: String,
    val target: String,
)

data class TexasExchangeTokenRequest(
    @JsonProperty("identity_provider") val identityProvider: String,
    val target: String,
    @JsonProperty("user_token") val userToken: String,
)

data class TexasTokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("expires_in")
    val expiresInSeconds: Long,
) {
    fun toAccessToken(): AccessToken =
        AccessToken(
            token = accessToken,
            expiresAt = Instant.now().plusSeconds(expiresInSeconds),
        ) {}
}
