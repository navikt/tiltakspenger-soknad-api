package no.nav.tiltakspenger.soknad.api.vedlegg

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import org.junit.jupiter.api.Test

/**
 * Selve avvisningen er verifisert ende-til-ende i `SøknadRoutesTest`.
 * Her dekkes kantene rundt grenseverdiene, som er dyre å sende gjennom en ekte multipart-request.
 */
internal class VedleggValideringTest {
    @Test
    fun `vedlegg på nøyaktig maks filstørrelse er gyldig`() {
        listOf(vedlegg(MAKS_FILSTØRRELSE_BYTES)).validerVedlegg()
    }

    @Test
    fun `ett byte over maks filstørrelse avvises`() {
        val feil = shouldThrow<RequestValidationException> {
            listOf(vedlegg(MAKS_FILSTØRRELSE_BYTES + 1)).validerVedlegg()
        }

        feil.reasons.single() shouldBe "1 av vedleggene er større enn maksgrensen på $MAKS_FILSTØRRELSE_BYTES bytes."
    }

    @Test
    fun `maks antall vedlegg er gyldig`() {
        List(MAKS_ANTALL_VEDLEGG) { vedlegg(størrelse = 1) }.validerVedlegg()
    }

    @Test
    fun `ett vedlegg over maks antall avvises`() {
        val feil = shouldThrow<RequestValidationException> {
            List(MAKS_ANTALL_VEDLEGG + 1) { vedlegg(størrelse = 1) }.validerVedlegg()
        }

        feil.reasons.single() shouldBe "Søknaden har ${MAKS_ANTALL_VEDLEGG + 1} vedlegg, men kan ha maks $MAKS_ANTALL_VEDLEGG."
    }

    @Test
    fun `vedlegg som til sammen treffer totalgrensen nøyaktig er gyldig`() {
        // Fem vedlegg på maks filstørrelse er nøyaktig totalgrensen.
        List(5) { vedlegg(MAKS_FILSTØRRELSE_BYTES) }.validerVedlegg()
    }

    @Test
    fun `vedlegg som til sammen er ett byte over totalgrensen avvises, selv om hvert enkelt er innenfor`() {
        // Alle seks er lovlige hver for seg og antallet er under grensen; det er kun summen som sprekker.
        val vedlegg = List(5) { vedlegg(MAKS_FILSTØRRELSE_BYTES) } + vedlegg(størrelse = 1)

        val feil = shouldThrow<RequestValidationException> { vedlegg.validerVedlegg() }

        feil.reasons.single() shouldBe
            "Vedleggene er til sammen ${MAKS_TOTAL_FILSTØRRELSE_BYTES + 1} bytes, men kan til sammen være maks $MAKS_TOTAL_FILSTØRRELSE_BYTES bytes."
    }

    @Test
    fun `alle bruddene rapporteres samtidig, ikke bare det første`() {
        val vedlegg = List(MAKS_ANTALL_VEDLEGG + 1) { vedlegg(MAKS_FILSTØRRELSE_BYTES + 1) }

        val feil = shouldThrow<RequestValidationException> { vedlegg.validerVedlegg() }

        feil.reasons shouldHaveSize 3
    }

    @Test
    fun `søknad uten vedlegg er gyldig`() {
        emptyList<Vedlegg>().validerVedlegg()
    }

    @Test
    fun `feilmeldingen lekker ikke filnavn til vanlig logg`() {
        val feil = shouldThrow<RequestValidationException> {
            listOf(vedlegg(MAKS_FILSTØRRELSE_BYTES + 1, filnavn = "sykmelding-ola-nordmann.pdf")).validerVedlegg()
        }

        feil.message!! shouldNotContain "ola-nordmann"
    }

    private fun vedlegg(størrelse: Int, filnavn: String = "vedlegg.pdf") = Vedlegg(
        filnavn = filnavn,
        contentType = "application/pdf",
        dokument = ByteArray(størrelse),
    )
}
