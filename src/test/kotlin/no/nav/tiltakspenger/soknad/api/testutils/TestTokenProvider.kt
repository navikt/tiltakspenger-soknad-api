package no.nav.tiltakspenger.soknad.api.testutils

import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider

/**
 * [AuthTokenProvider] som gir et fast token, og som husker om den ble bedt om å hoppe over cachen.
 * Tokenet er ikke et ekte JWT — klientene sender det bare videre som en bearer-streng.
 */
class TestTokenProvider(private val token: String = "test-token") : AuthTokenProvider {
    var sisteSkipCache: Boolean? = null
        private set

    override suspend fun hentToken(skipCache: Boolean): AccessToken {
        sisteSkipCache = skipCache
        return AccessToken(token = token, expiresAt = fixedClock.instant().plusSeconds(3600))
    }
}

val testTokenProvider: AuthTokenProvider get() = TestTokenProvider()
