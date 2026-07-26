package no.nav.tiltakspenger.soknad.api.testutils

import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.texas.IdentityProvider
import no.nav.tiltakspenger.libs.texas.client.TexasClient
import no.nav.tiltakspenger.libs.texas.client.TexasIntrospectionResponse
import no.nav.tiltakspenger.soknad.api.util.getGyldigTexasIntrospectionResponse
import java.time.Clock

/**
 * [TexasClient] for lokal kjøring, der ethvert token godtas som den samme testbrukeren.
 * Til forskjell fra [TexasClientFake], som kun godtar tokens en test har registrert, slipper man her å kjøre authserveren for å komme inn i appen.
 */
class TexasClientFakeLokal(
    private val clock: Clock,
    private val fnr: String = "12345678910",
) : TexasClient {
    override suspend fun introspectToken(
        token: String,
        identityProvider: IdentityProvider,
    ): TexasIntrospectionResponse = getGyldigTexasIntrospectionResponse(fnr = fnr, acr = "idporten-loa-high")

    override suspend fun getSystemToken(
        audienceTarget: String,
        identityProvider: IdentityProvider,
        rewriteAudienceTarget: Boolean,
        skipCache: Boolean,
    ): AccessToken = accessToken()

    override suspend fun exchangeToken(
        userToken: String,
        audienceTarget: String,
        identityProvider: IdentityProvider,
        skipCache: Boolean,
    ): AccessToken = accessToken()

    private fun accessToken() = AccessToken(
        token = "lokalt-token",
        expiresAt = clock.instant().plusSeconds(3600),
    )
}
