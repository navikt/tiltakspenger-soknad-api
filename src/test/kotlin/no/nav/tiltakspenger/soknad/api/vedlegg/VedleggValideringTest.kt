package no.nav.tiltakspenger.soknad.api.vedlegg

import io.kotest.assertions.throwables.shouldThrow
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
