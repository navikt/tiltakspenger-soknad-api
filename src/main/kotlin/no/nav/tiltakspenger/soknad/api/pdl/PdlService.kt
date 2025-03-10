package no.nav.tiltakspenger.soknad.api.pdl

import mu.KotlinLogging
import java.time.LocalDate

class PdlService(
    private val pdlClientTokenX: PdlClientTokenX,
    private val pdlClientCredentials: PdlCredentialsClient,
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
            pdlClientTokenX.fetchSøker(fødselsnummer = fødselsnummer, subjectToken = subjectToken, callId = callId)
        log.debug { "Henting av søkers personalia har gått OK. Kallid: $callId" }
        val person = result.toPerson()
        val barnsIdenter = person.barnsIdenter()
        log.debug { "Henter personalia søkers barn fra PDL. Kallid: $callId" }
        val barn = barnsIdenter
            .map { barnsIdent -> pdlClientCredentials.fetchBarn(barnsIdent, callId).getOrNull()?.toPerson() }
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
        log.debug { "Henter informasjon om adressebeskyttelse for fødselsnummer" }
        val result: AdressebeskyttelseRespons = pdlClientTokenX.fetchAdressebeskyttelse(
            fødselsnummer = fødselsnummer,
            subjectToken = subjectToken,
            callId = callId,
        )
        log.debug { "Hentet informasjon om adressebeskyttelse for fødselsnummer OK" }
        return result.toAdressebeskyttelseGradering()
    }
}
