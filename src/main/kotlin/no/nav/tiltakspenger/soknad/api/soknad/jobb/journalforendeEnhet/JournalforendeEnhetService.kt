package no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforendeEnhet

import mu.KotlinLogging
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering
import no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforendeEnhet.arbeidsfordeling.ArbeidsfordelingClient
import no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforendeEnhet.arbeidsfordeling.ArbeidsfordelingRequest
import no.nav.tiltakspenger.soknad.api.soknad.jobb.person.Person

class JournalforendeEnhetService(
    private val arbeidsfordelingClient: ArbeidsfordelingClient,
) {
    private val log = KotlinLogging.logger {}
    suspend fun finnJournalforendeEnhet(person: Person): String {
        val arbeidsfordelingRequest = ArbeidsfordelingRequest(
            diskresjonskode = getDiskresjonskode(person.adressebeskyttelseGradering),
            geografiskOmraade = person.geografiskTilknytning?.getGT(),
        )
        return arbeidsfordelingClient.hentArbeidsfordeling(arbeidsfordelingRequest)
            .also { log.info { "Fant journalførende enhet $it" } }
    }

    private fun getDiskresjonskode(adressebeskyttelseGradering: AdressebeskyttelseGradering) =
        when (adressebeskyttelseGradering) {
            AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND,
            AdressebeskyttelseGradering.STRENGT_FORTROLIG,
            -> "SPSF"

            AdressebeskyttelseGradering.FORTROLIG -> "SPFO"
            else -> null
        }
}
