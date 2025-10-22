package no.nav.tiltakspenger.soknad.api.pdl.client

import arrow.core.getOrElse
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklient
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.AdressebeskyttelseKunneIkkeAvklares
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.DeserializationException
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.FantIkkePerson
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.FødselKunneIkkeAvklares
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.Ikke2xx
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.IngenNavnFunnet
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.NavnKunneIkkeAvklares
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.NetworkError
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.ResponsManglerData
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.UkjentFeil
import no.nav.tiltakspenger.libs.texas.IdentityProvider
import no.nav.tiltakspenger.libs.texas.client.TexasHttpClient
import no.nav.tiltakspenger.soknad.api.objectMapper
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkerRespons
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkersBarnRespons
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.hentBarnQuery
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.hentPersonQuery
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

class PdlClient(
    endepunkt: String,
    private val pdlScope: String,
    private val texasClient: TexasHttpClient,
    private val getSystemToken: suspend () -> AccessToken,
) {
    private val log = KotlinLogging.logger {}
    private val cache: Cache<String, SøkerRespons> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(60))
        .build()

    private val personklient =
        FellesPersonklient.create(
            endepunkt = endepunkt,
            connectTimeout = 10.seconds,
            timeout = 10.seconds,
        )

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

    suspend fun fetchSøker(
        fødselsnummer: String,
        subjectToken: String,
        callId: String,
    ): SøkerRespons {
        log.info { "Henter søkers personopplysninger fra PDL med tokenx, callId $callId" }
        cache.getIfPresent(fødselsnummer)?.let {
            log.debug { "Hentet søkers personopplysninger fra cache, callId $callId" }
            return it
        }

        val token = texasClient.exchangeToken(
            userToken = subjectToken,
            audienceTarget = pdlScope,
            identityProvider = IdentityProvider.TOKENX,
        )
        return fetchSøkerFraPdl(
            fødselsnummer = fødselsnummer,
            token = token,
            callId = callId,
        )
    }

    suspend fun fetchSøkerSystembruker(
        fødselsnummer: String,
        callId: String,
    ): SøkerRespons {
        log.info { "Henter søkers personopplysninger fra PDL for systembruker, callId $callId" }
        cache.getIfPresent(fødselsnummer)?.let {
            log.debug { "Hentet søkers personopplysninger fra cache, callId $callId" }
            return it
        }
        return fetchSøkerFraPdl(
            fødselsnummer = fødselsnummer,
            token = getSystemToken(),
            callId = callId,
        )
    }

    private suspend fun fetchSøkerFraPdl(
        fødselsnummer: String,
        token: AccessToken,
        callId: String,
    ): SøkerRespons {
        log.info { "fetchSøker: Henter søkers personopplysninger fra PDL, callId $callId" }
        val body = objectMapper.writeValueAsString(hentPersonQuery(fødselsnummer))
        return personklient
            .graphqlRequest(token, body)
            .map {
                val response = objectMapper.readValue<SøkerRespons>(it)
                if (response.hentPerson != null) {
                    cache.put(fødselsnummer, response)
                }
                log.info { "fetchSøker: Hentet søkers personopplysninger fra PDL, callId $callId" }
                response
            }
            .getOrElse {
                it.mapError()
            }
    }
}

fun FellesPersonklientError.mapError(): Nothing {
    when (this) {
        is AdressebeskyttelseKunneIkkeAvklares -> throw RuntimeException(
            "Feil ved henting av personopplysninger: AdressebeskyttelseKunneIkkeAvklares",
        )

        is DeserializationException -> throw RuntimeException(
            "Feil ved henting av personopplysninger: DeserializationException",
            this.exception,
        )

        is FantIkkePerson -> throw RuntimeException("Feil ved henting av personopplysninger: FantIkkePerson")
        is FødselKunneIkkeAvklares -> throw RuntimeException("Feil ved henting av personopplysninger: FødselKunneIkkeAvklares")
        is Ikke2xx -> throw RuntimeException("Feil ved henting av personopplysninger: $this")
        is IngenNavnFunnet -> throw RuntimeException("Feil ved henting av personopplysninger: IngenNavnFunnet")
        is NavnKunneIkkeAvklares -> throw RuntimeException("Feil ved henting av personopplysninger: NavnKunneIkkeAvklares")
        is NetworkError -> throw RuntimeException(
            "Feil ved henting av personopplysninger: NetworkError",
            this.exception,
        )

        is ResponsManglerData -> throw RuntimeException("Feil ved henting av personopplysninger: ResponsManglerPerson")
        is UkjentFeil -> throw RuntimeException("Feil ved henting av personopplysninger: $this")
    }
}
