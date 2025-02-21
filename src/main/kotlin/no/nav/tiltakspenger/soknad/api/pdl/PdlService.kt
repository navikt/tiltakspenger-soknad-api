package no.nav.tiltakspenger.soknad.api.pdl

import mu.KotlinLogging
import java.time.LocalDate

class PdlService(
    private val pdlClientTokenX: PdlClientTokenX,
    private val pdlClientCredentials: PdlCredentialsClient,
) {
    private val log = KotlinLogging.logger {}

    suspend fun hentPersonaliaMedBarn(fødselsnummer: String, subjectToken: String, callId: String, styrendeDato: LocalDate = LocalDate.now()): PersonDTO {
        log.info { "Henter søkers personalia fra PDL" }
        val result = pdlClientTokenX.fetchSøker(fødselsnummer = fødselsnummer, subjectToken = subjectToken, callId = callId)
        if (result.isSuccess) {
            log.info { "Henting søkers personalia har gått OK" }
            val person = result.getOrNull()!!.toPerson()
            val barnsIdenter = person.barnsIdenter()
            log.info { "Henter personalia søkers barn fra PDL" }
            val barn = barnsIdenter
                .map { barnsIdent -> pdlClientCredentials.fetchBarn(barnsIdent, callId).getOrNull()?.toPerson() }
                .mapNotNull { it }
                .filter { it.erUnder16ÅrPåDato(dato = styrendeDato) }
            log.info { "Henting personalia søkers barn har gått OK" }
            return person.toPersonDTO(barn)
        }

        log.error { "Noe gikk galt under kall til PDL" }
        throw IllegalStateException("Noe gikk galt under kall til PDL")
    }

    suspend fun hentAdressebeskyttelse(fødselsnummer: String, subjectToken: String, callId: String): AdressebeskyttelseGradering {
        log.info { "Henter informasjon om adressebeskyttelse" }
        val result = pdlClientTokenX.fetchAdressebeskyttelse(fødselsnummer = fødselsnummer, subjectToken = subjectToken, callId = callId)
        if (result.isSuccess) {
            log.info { "Henting søkers adressebeskyttelse har gått OK" }
            return result.getOrNull()!!.toAdressebeskyttelseGradering()
        }

        log.error { "Noe gikk galt under kall til PDL ved henting av adressebeskyttelse" }
        throw IllegalStateException("Noe gikk galt under kall til PDL ved henting av adressebeskyttelse")
    }
}
