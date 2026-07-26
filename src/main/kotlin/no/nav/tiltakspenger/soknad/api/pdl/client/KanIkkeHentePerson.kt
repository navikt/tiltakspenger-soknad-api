package no.nav.tiltakspenger.soknad.api.pdl.client

import arrow.core.Nel
import io.github.oshai.kotlinlogging.KLogger
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata
import no.nav.tiltakspenger.libs.httpklient.loggFeil
import no.nav.tiltakspenger.libs.logging.Sikkerlogg

/**
 * Feil ved oppslag mot PDL.
 * Alle variantene er fatale for kalleren — akkurat som før migreringen, der enhver PDL-feil endte som en kastet exception.
 *
 * Feilen bærer HTTP-konteksten videre i stedet for at kallstedene plukker den fra hverandre; [logg] er den ene logghendelsen per feilsituasjon.
 */
sealed interface KanIkkeHentePerson {
    /** Selve HTTP-kallet feilet (status, nettverk, timeout, deserialisering). */
    data class KallFeilet(val feil: HttpKlientError) : KanIkkeHentePerson

    /**
     * PDL svarte `200 OK` med en ikke-tom `errors`-liste.
     * GraphQL svarer av design `200` også på funksjonelle feil, så statusregelen kan ikke fange dette.
     */
    data class GraphQLFeil(
        val feilmeldinger: Nel<String>,
        val metadata: HttpKlientMetadata,
    ) : KanIkkeHentePerson

    /** PDL svarte `200 OK` uten `errors`, men også uten `data` — et svar vi ikke kan gjøre noe med. */
    data class ResponsManglerData(val metadata: HttpKlientMetadata) : KanIkkeHentePerson
}

/**
 * Logger feilen én gang, med vanlig logg uten PII og full request/respons i sikkerlogg.
 * [operasjon] og [kontekst] er det kalleren vet mer om enn klienten.
 *
 * Bevisst en extension og ikke en medlemsfunksjon: en default-implementert medlemsfunksjon på et interface kompilerer til en `DefaultImpls`-bro som ikke lar seg dekke fra tester.
 */
fun KanIkkeHentePerson.logg(logger: KLogger, operasjon: String, kontekst: String, sikkerlogg: Sikkerlogg) {
    when (this) {
        is KanIkkeHentePerson.KallFeilet -> feil.loggFeil(logger, operasjon, kontekst, sikkerlogg)

        is KanIkkeHentePerson.GraphQLFeil -> {
            logger.error { "Feil ved $operasjon. $kontekst. PDL svarte 200 med feil i errors-lista. ${sikkerlogg.seSikkerlogg}" }
            sikkerlogg.error { "Feil ved $operasjon. $kontekst. PDL-feilmeldinger: $feilmeldinger. Request: ${metadata.rawRequestString}. Response: ${metadata.rawResponseString}." }
        }

        is KanIkkeHentePerson.ResponsManglerData -> {
            logger.error { "Feil ved $operasjon. $kontekst. PDL svarte 200 uten data. ${sikkerlogg.seSikkerlogg}" }
            sikkerlogg.error { "Feil ved $operasjon. $kontekst. Request: ${metadata.rawRequestString}. Response: ${metadata.rawResponseString}." }
        }
    }
}
