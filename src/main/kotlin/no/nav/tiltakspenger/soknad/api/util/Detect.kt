package no.nav.tiltakspenger.soknad.api.util

import no.nav.tiltakspenger.soknad.api.util.Detect.detect
import no.nav.tiltakspenger.soknad.api.util.Detect.godkjenteFiltyper
import org.apache.tika.Tika

object Detect {
    const val IMAGE_PNG = "image/png"
    const val IMAGE_JPEG = "image/jpeg"
    const val APPLICATON_PDF = "application/pdf"

    val godkjenteFiltyper = listOf(IMAGE_JPEG, IMAGE_PNG, APPLICATON_PDF)

    private val tika: Tika = Tika()

    /**
     * Bestemmer innholdstypen fra innholdet selv, ikke fra filnavn eller oppgitt Content-Type.
     * Vedlegg kommer fra brukeren, så det er kun signaturen i bytene vi kan stole på.
     */
    fun ByteArray.detect(): String = tika.detect(this)
}

fun sjekkContentType(filInnholdBa: ByteArray) = filInnholdBa.detect().takeIf { godkjenteFiltyper.contains(it) } ?: throw UnsupportedContentException("Vedleggstype ikke støttet!")

class UnsupportedContentException(message: String) : RuntimeException(message)
