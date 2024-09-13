package no.nav.tiltakspenger.soknad.api.soknad.jobb.person

import arrow.core.flatMap
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.Fnr
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
import no.nav.tiltakspenger.libs.personklient.pdl.dto.avklarNavn
import no.nav.tiltakspenger.soknad.api.objectMapper

class PersonHttpklient(
    endepunkt: String,
    private val getSystemToken: suspend () -> AccessToken,
) : PersonGateway {
    private val personklient =
        FellesPersonklient.create(
            endepunkt = endepunkt,
        )

    override suspend fun hentNavnForFnr(
        fnr: Fnr,
    ): no.nav.tiltakspenger.libs.personklient.pdl.dto.Navn {
        val body = objectMapper.writeValueAsString(hentPersonNavnQuery(fnr))
        return personklient
            .hentPerson(fnr, getSystemToken(), body)
            .flatMap { jsonBody: String ->
                val data: PdlResponseData = objectMapper.readValue<PdlResponseData>(jsonBody)
                avklarNavn(data.hentPerson.navn)
            }.onLeft {
                it.logError()
            }.getOrNull()!!
    }
}

private data class PdlResponseData(
    val hentPerson: PdlPerson,
) {
    data class PdlPerson(
        val navn: List<no.nav.tiltakspenger.libs.personklient.pdl.dto.Navn>,
    )
}

private fun FellesPersonklientError.logError(): Nothing {
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