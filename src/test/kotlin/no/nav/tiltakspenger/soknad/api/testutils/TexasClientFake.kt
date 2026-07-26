package no.nav.tiltakspenger.soknad.api.testutils

import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.texas.IdentityProvider
import no.nav.tiltakspenger.libs.texas.client.TexasClient
import no.nav.tiltakspenger.libs.texas.client.TexasIntrospectionResponse
import no.nav.tiltakspenger.soknad.api.util.getGyldigTexasIntrospectionResponse
import no.nav.tiltakspenger.soknad.api.util.lagTestToken
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * [TexasClient] som godkjenner tokens testen har registrert, og avviser alle andre.
 * Erstatter mocking av introspeksjonen i rute-testene.
 */
class TexasClientFake(
    private val clock: Clock,
) : TexasClient {
    private val gyldigeTokens = ConcurrentHashMap<String, Claims>()

    private data class Claims(val fnr: String, val acr: String)

    /**
     * Lager et test-token for [fnr] og registrerer det som gyldig.
     * Returnerer den serialiserte tokenstrengen, som er det testen sender i `Authorization`-headeren.
     */
    fun leggTilBrukertoken(fnr: String, acr: String = "idporten-loa-high"): String {
        val token = lagTestToken(mapOf("acr" to acr, "pid" to fnr)).serialize()
        gyldigeTokens[token] = Claims(fnr = fnr, acr = acr)
        return token
    }

    override suspend fun introspectToken(
        token: String,
        identityProvider: IdentityProvider,
    ): TexasIntrospectionResponse {
        val claims = gyldigeTokens[token] ?: return TexasIntrospectionResponse(
            active = false,
            error = "Ugyldig token",
            groups = null,
            roles = null,
        )
        return getGyldigTexasIntrospectionResponse(fnr = claims.fnr, acr = claims.acr)
    }

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
        token = "test-access-token",
        expiresAt = clock.instant().plusSeconds(3600),
    )
}
