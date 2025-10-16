package no.nav.tiltakspenger.soknad.api.pdl

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.soknad.api.pdl.client.PdlClient
import no.nav.tiltakspenger.soknad.api.pdl.routes.dto.PersonDTO
import java.time.LocalDate

class PdlService(
    private val pdlClient: PdlClient,
) {
    private val log = KotlinLogging.logger {}

    suspend fun hentPersonaliaMedBarn(
        fødselsnummer: String,
        subjectToken: String,
        callId: String,
        styrendeDato: LocalDate = LocalDate.now(),
    ): PersonDTO {
        log.debug { "Henter søkers personalia fra PDL. Kallid: $callId" }
        val result =
            pdlClient.fetchSøker(fødselsnummer = fødselsnummer, subjectToken = subjectToken, callId = callId)
        log.debug { "Henting av søkers personalia har gått OK. Kallid: $callId" }
        val person = result.toPerson()
        val barnsIdenter = person.barnsIdenter()
        log.debug { "Henter personalia søkers barn fra PDL. Kallid: $callId" }
        val barn = barnsIdenter
            .map { barnsIdent -> pdlClient.fetchBarn(barnsIdent, callId).getOrNull()?.toPerson() }
            .mapNotNull { it }
            .filter { it.erUnder16ÅrPåDato(dato = styrendeDato) }
        log.debug { "Henting personalia søkers barn har gått OK. Kallid: $callId" }
        return person.toPersonDTO(barn)
    }

    suspend fun hentAdressebeskyttelse(
        fødselsnummer: String,
        subjectToken: String,
        callId: String,
    ): AdressebeskyttelseGradering {
        log.debug { "Henter informasjon om adressebeskyttelse for fødselsnummer, callId $callId" }
        val personopplysninger = pdlClient.fetchSøker(fødselsnummer = fødselsnummer, subjectToken = subjectToken, callId = callId)
        val adressebeskyttelse = personopplysninger.toPerson().adressebeskyttelseGradering
        log.debug { "Hentet informasjon om adressebeskyttelse for fødselsnummer OK, callId $callId" }
        return adressebeskyttelse
    }

    suspend fun hentNavnForFnr(
        fnr: Fnr,
        correlationId: CorrelationId,
    ): Navn {
        val callId = correlationId.toString()
        log.debug { "Henter navn for fødselsnummer, callId $callId" }
        val personopplysninger = pdlClient.fetchSøkerSystembruker(
            fødselsnummer = fnr.verdi,
            callId = callId,
        )
        log.debug { "Hentet navn for fødselsnummer, callId $callId" }
        return personopplysninger.toPerson().getNavn()
    }
}
