package no.nav.tiltakspenger.soknad.api.antivirus

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

class AvService(
    private val clamAvClient: ClamAvClient,
) {

    private val log = KotlinLogging.logger { }

    suspend fun gjørVirussjekkAvVedlegg(vedleggsListe: List<Vedlegg>) {
        val resultat = clamAvClient.scan(vedleggsListe)
        val virusErFunnet = resultat.any { it.resultat == Status.FOUND }

        resultat.forEach {
            if (it.resultat == Status.FOUND) {
                log.info { "Fant skadevare i vedlegg ${it.filnavn}" }
            }
            if (it.resultat === Status.ERROR) {
                val feilmelding = "Noe gikk galt under virusscan av fil ${it.filnavn}"
                log.error { feilmelding }
                throw RuntimeException(feilmelding)
            }
        }

        if (virusErFunnet) {
            throw MalwareFoundException("Skadevare funnet i vedlegg")
        }

        log.info { "Virussjekk ok" }
    }
}

class MalwareFoundException(message: String) : RuntimeException(message)
