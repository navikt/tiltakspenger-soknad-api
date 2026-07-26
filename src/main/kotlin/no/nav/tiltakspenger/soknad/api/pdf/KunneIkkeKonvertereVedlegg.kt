package no.nav.tiltakspenger.soknad.api.pdf

import no.nav.tiltakspenger.libs.httpklient.HttpKlientError

/**
 * Feil under konvertering av vedlegg til PDF.
 * Begge variantene var kastede exceptions før migreringen, og begge er fortsatt fatale for journalføringen av den enkelte søknaden.
 */
sealed interface KunneIkkeKonvertereVedlegg {
    /** Kallet til pdfgen feilet. */
    data class KallFeilet(val feil: HttpKlientError) : KunneIkkeKonvertereVedlegg

    /** Vedlegget er ikke PDF, PNG eller JPEG — noe valideringen ved innsending skulle ha stoppet. */
    data class UgyldigFilformat(val contentType: String) : KunneIkkeKonvertereVedlegg
}
