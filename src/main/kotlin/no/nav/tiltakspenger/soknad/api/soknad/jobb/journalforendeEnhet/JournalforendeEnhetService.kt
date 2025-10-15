package no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforendeEnhet

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.soknad.api.pdl.AdressebeskyttelseGradering
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.GeografiskTilknytning
import no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforendeEnhet.arbeidsfordeling.ArbeidsfordelingClient
import no.nav.tiltakspenger.soknad.api.soknad.jobb.journalforendeEnhet.arbeidsfordeling.ArbeidsfordelingRequest

class JournalforendeEnhetService(
    private val arbeidsfordelingClient: ArbeidsfordelingClient,
) {
    private val log = KotlinLogging.logger {}
    suspend fun finnJournalforendeEnhet(
        adressebeskyttelseGradering: AdressebeskyttelseGradering,
        geografiskTilknytning: GeografiskTilknytning?,
    ): String {
        val arbeidsfordelingRequest = ArbeidsfordelingRequest(
            diskresjonskode = getDiskresjonskode(adressebeskyttelseGradering),
            geografiskOmraade = geografiskTilknytning?.getGT(),
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
