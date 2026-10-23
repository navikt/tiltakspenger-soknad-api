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

    private sealed interface Claims {
        val identityProvider: IdentityProvider

        data class Bruker(val fnr: String, val acr: String) : Claims {
            override val identityProvider = IdentityProvider.TOKENX
        }

        data class Azure(val azpName: String, val erSystembruker: Boolean) : Claims {
            override val identityProvider = IdentityProvider.AZUREAD
        }
    }

    /**
     * Lager et test-token for [fnr] og registrerer det som gyldig.
     * Returnerer den serialiserte tokenstrengen, som er det testen sender i `Authorization`-headeren.
     */
    fun leggTilBrukertoken(fnr: String, acr: String = "idporten-loa-high"): String {
        val token = lagTestToken(mapOf("acr" to acr, "pid" to fnr)).serialize()
        gyldigeTokens[token] = Claims.Bruker(fnr = fnr, acr = acr)
        return token
    }

    /**
     * Lager et Azure AD-token for en annen app, som saksbehandling-api, og registrerer det som gyldig.
     * Med [erSystembruker] lik false later tokenet som det tilhører en innlogget ansatt.
     */
    fun leggTilAzureToken(
        azpName: String = "dev-gcp:tpts:tiltakspenger-saksbehandling-api",
        erSystembruker: Boolean = true,
    ): String {
        val token = lagTestToken(mapOf("azp_name" to azpName, "idtyp" to if (erSystembruker) "app" else "user")).serialize()
        gyldigeTokens[token] = Claims.Azure(azpName = azpName, erSystembruker = erSystembruker)
        return token
    }

    override suspend fun introspectToken(
        token: String,
        identityProvider: IdentityProvider,
    ): TexasIntrospectionResponse {
        val claims = gyldigeTokens[token]?.takeIf { it.identityProvider == identityProvider } ?: return TexasIntrospectionResponse(
            active = false,
            error = "Ugyldig token",
            groups = null,
            roles = null,
        )
        return when (claims) {
            is Claims.Bruker -> getGyldigTexasIntrospectionResponse(fnr = claims.fnr, acr = claims.acr)

            is Claims.Azure -> TexasIntrospectionResponse(
                active = true,
                error = null,
                groups = null,
                roles = emptyList(),
                other = buildMap {
                    put("azp_name", claims.azpName)
                    put("azp", "${claims.azpName}-id")
                    if (claims.erSystembruker) put("idtyp", "app")
                },
            )
        }
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
