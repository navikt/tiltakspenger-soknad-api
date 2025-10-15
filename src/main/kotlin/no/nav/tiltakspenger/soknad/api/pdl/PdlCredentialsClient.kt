package no.nav.tiltakspenger.soknad.api.pdl

import arrow.core.getOrElse
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklient
import no.nav.tiltakspenger.soknad.api.objectMapper
import no.nav.tiltakspenger.soknad.api.soknad.jobb.person.mapError

class PdlCredentialsClient(
    endepunkt: String,
    private val getSystemToken: suspend () -> AccessToken,
) {
    private val log = KotlinLogging.logger {}

    private val personklient =
        FellesPersonklient.create(endepunkt = endepunkt)

    suspend fun fetchBarn(
        ident: String,
        callId: String,
    ): Result<SøkersBarnRespons> {
        log.info { "fetchBarn: Henter barn fra PDL, callId $callId" }
        val body = objectMapper.writeValueAsString(hentBarnQuery(ident))
        return personklient
            .graphqlRequest(getSystemToken(), body)
            .map {
                Result.success(objectMapper.readValue<SøkersBarnRespons>(it))
            }.getOrElse {
                Result.failure<SøkersBarnRespons>(it.mapError())
            }
    }
}
