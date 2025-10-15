package no.nav.tiltakspenger.soknad.api.pdl

import arrow.core.getOrElse
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklient
import no.nav.tiltakspenger.libs.texas.IdentityProvider
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.soknad.api.objectMapper
import no.nav.tiltakspenger.soknad.api.soknad.jobb.person.mapError

class PdlClientTokenX(
    endepunkt: String,
    private val pdlScope: String,
    private val texasClient: TexasHttpClient,
) {
    private val log = KotlinLogging.logger {}
    private val cache: Cache<String, AdressebeskyttelseRespons> = Caffeine.newBuilder()
        .expireAfterWrite(java.time.Duration.ofMinutes(60))
        .build()

    private val personklient =
        FellesPersonklient.create(endepunkt = endepunkt)

    suspend fun fetchSøker(
        fødselsnummer: String,
        subjectToken: String,
        callId: String,
    ): SøkerRespons {
        log.info { "fetchSøker: Henter søkers personopplysninger fra PDL, callId $callId" }
        val body = objectMapper.writeValueAsString(hentPersonQuery(fødselsnummer))
        val token = texasClient.exchangeToken(
            userToken = subjectToken,
            audienceTarget = pdlScope,
            identityProvider = IdentityProvider.TOKENX,
        )
        return personklient
            .graphqlRequest(token, body)
            .map {
                objectMapper.readValue<SøkerRespons>(it)
                    .also { log.info { "fetchSøker: Hentet søkers personopplysninger fra PDL, callId $callId" } }
            }
            .getOrElse {
                it.mapError()
            }
    }

    suspend fun fetchAdressebeskyttelse(
        fødselsnummer: String,
        subjectToken: String,
        callId: String,
    ): AdressebeskyttelseRespons {
        cache.getIfPresent(fødselsnummer)?.let {
            log.debug { "Hentet adressebeskyttelse fra cache, callId $callId" }
            return it
        }
        log.info { "Henter adressebeskyttelse fra PDL, callId $callId" }
        val body = objectMapper.writeValueAsString(hentAdressebeskyttelseQuery(fødselsnummer))
        val token = texasClient.exchangeToken(
            userToken = subjectToken,
            audienceTarget = pdlScope,
            identityProvider = IdentityProvider.TOKENX,
        )
        return personklient
            .graphqlRequest(token, body)
            .map {
                val response = objectMapper.readValue<AdressebeskyttelseRespons>(it)
                if (response.errors.isEmpty()) {
                    cache.put(fødselsnummer, response)
                }
                log.info { "Hentet adressebeskyttelse fra PDL, callId $callId" }
                response
            }
            .getOrElse {
                it.mapError()
            }
    }
}
