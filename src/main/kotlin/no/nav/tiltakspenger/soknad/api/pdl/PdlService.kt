package no.nav.tiltakspenger.soknad.api.pdl

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.toNonEmptyListOrNull
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.soknad.api.pdl.client.KanIkkeHentePerson
import no.nav.tiltakspenger.soknad.api.pdl.client.PersonKlient
import no.nav.tiltakspenger.soknad.api.pdl.client.logg
import no.nav.tiltakspenger.soknad.api.pdl.routes.dto.PersonDTO
import java.time.Clock
import java.time.LocalDate

class PdlService(
    private val personKlient: PersonKlient,
    private val clock: Clock,
    private val sikkerlogg: Sikkerlogg,
) {
    private val log = KotlinLogging.logger {}

    /**
     * Barn filtreres på om de er under 16 år på [styrendeDato].
     * Dersom [styrendeDato] er null brukes dagens dato.
     */
    suspend fun hentPersonaliaMedBarn(
        fødselsnummer: String,
        subjectToken: String,
        callId: String,
        styrendeDato: LocalDate?,
    ): Either<KanIkkeHentePerson, PersonDTO> = either {
        val filtreringsdato = styrendeDato ?: LocalDate.now(clock)
        val person = personKlient.fetchSøker(fødselsnummer = fødselsnummer, subjectToken = subjectToken)
            .loggFeil("henting av søkers personalia fra PDL", callId)
            .bind()
            .toPerson(Fnr.fromString(fødselsnummer))
        val barn = person.barnsIdenter().toNonEmptyListOrNull()?.let { identer ->
            personKlient.fetchBarn(identer)
                .loggFeil("henting av personalia for søkers barn fra PDL", callId)
                .bind()
                .toPersoner()
                .filterNotNull()
                .filter { it.erUnder16ÅrPåDato(dato = filtreringsdato) }
        }.orEmpty()
        person.toPersonDTO(dagensDato = LocalDate.now(clock), barn = barn)
    }

    suspend fun hentAdressebeskyttelse(
        fødselsnummer: String,
        subjectToken: String,
        callId: String,
    ): Either<KanIkkeHentePerson, AdressebeskyttelseGradering> =
        personKlient.fetchSøker(fødselsnummer = fødselsnummer, subjectToken = subjectToken)
            .loggFeil("henting av adressebeskyttelse fra PDL", callId)
            .map { it.toPerson(Fnr.fromString(fødselsnummer)).adressebeskyttelseGradering }

    suspend fun hentNavnForFnr(
        fnr: Fnr,
        correlationId: CorrelationId,
    ): Either<KanIkkeHentePerson, Navn> =
        personKlient.fetchSøkerSystembruker(fødselsnummer = fnr.verdi)
            .loggFeil("henting av navn fra PDL", correlationId.toString())
            .map { it.toPerson(fnr).getNavn() }

    /** Én logghendelse per feilsituasjon, fra laget som har domenekonteksten. */
    private fun <T> Either<KanIkkeHentePerson, T>.loggFeil(
        operasjon: String,
        callId: String,
    ): Either<KanIkkeHentePerson, T> = onLeft { it.logg(log, operasjon, "callId $callId", sikkerlogg) }
}
