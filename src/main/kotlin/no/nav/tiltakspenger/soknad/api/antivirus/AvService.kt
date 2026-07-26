package no.nav.tiltakspenger.soknad.api.antivirus

import arrow.core.Either
import arrow.core.Nel
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.loggFeil
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

/**
 * Utfallet når en virussjekk ikke er «ren».
 * [SkadevareFunnet] skyldes det brukeren lastet opp og gir `400` fra ruta; de øvrige er våre og gir `500` — samme oppdeling som før migreringen.
 */
sealed interface VirussjekkFeil {
    data object SkadevareFunnet : VirussjekkFeil

    /** ClamAV svarte, men klarte ikke å skanne én eller flere av filene. */
    data class SkanningFeilet(val filnavn: Nel<String>) : VirussjekkFeil

    /** Selve kallet til ClamAV feilet. */
    data class KallFeilet(val feil: HttpKlientError) : VirussjekkFeil
}

class AvService(
    private val clamAvClient: ClamAvClient,
    private val sikkerlogg: Sikkerlogg,
) {
    private val log = KotlinLogging.logger { }

    suspend fun gjørVirussjekkAvVedlegg(vedleggsListe: Nel<Vedlegg>): Either<VirussjekkFeil, Unit> =
        clamAvClient.scan(vedleggsListe).mapLeft { feil ->
            feil.loggFeil(log, "virussjekk av vedlegg", "Antall vedlegg: ${vedleggsListe.size}", sikkerlogg)
            VirussjekkFeil.KallFeilet(feil)
        }.flatMap { resultat -> resultat.tilUtfall() }

    private fun List<AvSjekkResultat>.tilUtfall(): Either<VirussjekkFeil, Unit> {
        val filerSomIkkeBleSkannet = filter { it.resultat == Status.ERROR }.map { it.filnavn }.toNonEmptyListOrNull()
        if (filerSomIkkeBleSkannet != null) {
            log.error { "Klarte ikke å virusskanne ${filerSomIkkeBleSkannet.size} av filene. ${sikkerlogg.seSikkerlogg}" }
            sikkerlogg.error { "Klarte ikke å virusskanne filene $filerSomIkkeBleSkannet" }
            return VirussjekkFeil.SkanningFeilet(filerSomIkkeBleSkannet).left()
        }

        val filerMedSkadevare = filter { it.resultat == Status.FOUND }.map { it.filnavn }.toNonEmptyListOrNull()
        if (filerMedSkadevare != null) {
            log.info { "Fant skadevare i ${filerMedSkadevare.size} vedlegg. ${sikkerlogg.seSikkerlogg}" }
            sikkerlogg.info { "Fant skadevare i vedleggene $filerMedSkadevare" }
            return VirussjekkFeil.SkadevareFunnet.left()
        }

        log.info { "Virussjekk ok" }
        return Unit.right()
    }
}
