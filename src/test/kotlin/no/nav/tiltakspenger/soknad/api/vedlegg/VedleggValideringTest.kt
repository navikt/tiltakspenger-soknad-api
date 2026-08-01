package no.nav.tiltakspenger.soknad.api.vedlegg

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.tiltakspenger.soknad.api.util.Detect
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

/**
 * Selve avvisningen er verifisert ende-til-ende i `SøknadRoutesTest`.
 * Her dekkes kantene rundt grenseverdiene, som er dyre å sende gjennom en ekte multipart-request.
 */
class VedleggValideringTest {

    // ---- Størrelse og antall ----

    @Test
    fun `vedlegg på nøyaktig maks filstørrelse er gyldig`() {
        listOf(bildeVedlegg(MAKS_FILSTØRRELSE_BYTES)).validerVedlegg().isRight() shouldBe true
    }

    @Test
    fun `ett byte over maks filstørrelse avvises`() {
        val feil = listOf(bildeVedlegg(MAKS_FILSTØRRELSE_BYTES + 1)).validerVedlegg().leftOrNull()!!

        feil.single().melding shouldBe "1 av vedleggene er større enn maksgrensen på $MAKS_FILSTØRRELSE_BYTES bytes."
    }

    @Test
    fun `maks antall vedlegg er gyldig`() {
        List(MAKS_ANTALL_VEDLEGG) { bildeVedlegg(størrelse = 1) }.validerVedlegg().isRight() shouldBe true
    }

    @Test
    fun `ett vedlegg over maks antall avvises`() {
        val feil = List(MAKS_ANTALL_VEDLEGG + 1) { bildeVedlegg(størrelse = 1) }.validerVedlegg().leftOrNull()!!

        feil.single().melding shouldBe "Søknaden har ${MAKS_ANTALL_VEDLEGG + 1} vedlegg, men kan ha maks $MAKS_ANTALL_VEDLEGG."
    }

    @Test
    fun `vedlegg som til sammen treffer totalgrensen nøyaktig er gyldig`() {
        // Fem vedlegg på maks filstørrelse er nøyaktig totalgrensen.
        List(5) { bildeVedlegg(MAKS_FILSTØRRELSE_BYTES) }.validerVedlegg().isRight() shouldBe true
    }

    @Test
    fun `vedlegg som til sammen er ett byte over totalgrensen avvises, selv om hvert enkelt er innenfor`() {
        // Alle seks er lovlige hver for seg og antallet er under grensen; det er kun summen som sprekker.
        val vedlegg = List(5) { bildeVedlegg(MAKS_FILSTØRRELSE_BYTES) } + bildeVedlegg(størrelse = 1)

        val feil = vedlegg.validerVedlegg().leftOrNull()!!

        feil.single().melding shouldBe
            "Vedleggene er til sammen ${MAKS_TOTAL_FILSTØRRELSE_BYTES + 1} bytes, men kan til sammen være maks $MAKS_TOTAL_FILSTØRRELSE_BYTES bytes."
    }

    @Test
    fun `alle bruddene rapporteres samtidig, ikke bare det første`() {
        val vedlegg = List(MAKS_ANTALL_VEDLEGG + 1) { bildeVedlegg(MAKS_FILSTØRRELSE_BYTES + 1) }

        val feil = vedlegg.validerVedlegg().leftOrNull()!!

        feil shouldHaveSize 3
    }

    @Test
    fun `søknad uten vedlegg er gyldig`() {
        emptyList<Vedlegg>().validerVedlegg().isRight() shouldBe true
    }

    @Test
    fun `feilmeldingen lekker ikke filnavn`() {
        val feil = listOf(
            bildeVedlegg(MAKS_FILSTØRRELSE_BYTES + 1, filnavn = "sykmelding-ola-nordmann.pdf"),
        ).validerVedlegg().leftOrNull()!!

        feil.single().melding shouldNotContain "ola-nordmann"
    }

    // ---- PDF-vakten ----

    @Test
    fun `en vanlig pdf på flere sider er gyldig`() {
        listOf(pdfVedlegg(antallSider = 3)).validerVedlegg().isRight() shouldBe true
    }

    /**
     * Selve sikkerhetsfiksen.
     * En side på 14400×14400 pt er under 600 bytes på disk, men ville blitt en `BufferedImage` på 791 MiB ved rendring — mer enn hele heapen.
     * Sperren er derfor på sidens geometri, ikke på filstørrelsen.
     */
    @Test
    fun `pdf med en side som er for stor til å rendres avvises`() {
        val feil = listOf(pdfVedlegg(sidestørrelse = PDRectangle(14400f, 14400f))).validerVedlegg().leftOrNull()!!

        feil.single() shouldBe VedleggValideringsfeil.PdfSideErForStor(størsteSideIPiksler = 14400L * 14400L)
        // Meldingen går til brukeren, så den må si hva som er galt uten å nevne filnavnet.
        feil.single().melding shouldBe
            "Et av vedleggene har en side som er for stor til å behandles (${14400L * 14400L} piksler, maks $MAKS_PIKSLER_PER_PDF_SIDE)."
    }

    @Test
    fun `pdf like under pikselgrensen godtas`() {
        val nesteMaks = Math.sqrt(MAKS_PIKSLER_PER_PDF_SIDE.toDouble()).toFloat() - 1f

        listOf(pdfVedlegg(sidestørrelse = PDRectangle(nesteMaks, nesteMaks))).validerVedlegg().isRight() shouldBe true
    }

    @Test
    fun `pdf med maks antall sider er gyldig`() {
        listOf(pdfVedlegg(antallSider = MAKS_SIDER_I_PDF)).validerVedlegg().isRight() shouldBe true
    }

    @Test
    fun `pdf med for mange sider avvises`() {
        val feil = listOf(pdfVedlegg(antallSider = MAKS_SIDER_I_PDF + 1)).validerVedlegg().leftOrNull()!!

        feil.single().shouldBeInstanceOf<VedleggValideringsfeil.PdfHarForMangeSider>()
            .antallSider shouldBe MAKS_SIDER_I_PDF + 1
    }

    /**
     * Rendringen går på cropBox, ikke mediaBox.
     * En pdf med stor mediaBox og liten cropBox rendres i det små og skal derfor godtas — ellers ville vakten avvist lovlige filer.
     */
    @Test
    fun `stor mediaBox med liten cropBox godtas, fordi det er cropBox som rendres`() {
        val pdf = pdf {
            PDPage(PDRectangle(14400f, 14400f)).also { it.cropBox = PDRectangle(595f, 842f) }
        }

        listOf(vedlegg(pdf, Detect.APPLICATON_PDF)).validerVedlegg().isRight() shouldBe true
    }

    /**
     * En avkortet eller ødelagt pdf passerer magic byte-sjekken i [Detect], men pdfbox klarer ikke å lese den.
     * Uten denne grenen dukket feilen først opp i journalføringsjobben, lenge etter at brukeren hadde fått kvittering.
     */
    @Test
    fun `korrupt pdf avvises i stedet for å kaste`() {
        val vedlegg = listOf(vedlegg("%PDF-1.4\nikke en ekte pdf".toByteArray(), Detect.APPLICATON_PDF))

        val feil = vedlegg.validerVedlegg().leftOrNull()!!

        feil.single().shouldBeInstanceOf<VedleggValideringsfeil.PdfKunneIkkeLeses>()
    }

    @Test
    fun `bildevedlegg forsøkes ikke lest som pdf`() {
        // Ville gitt PdfKunneIkkeLeses hvis vi parset alt som pdf.
        listOf(bildeVedlegg(størrelse = 100)).validerVedlegg().isRight() shouldBe true
    }

    @Test
    fun `en for stor pdf leses ikke, selv om den er ugyldig`() {
        // Vi skal ikke la pdfbox røre en fil vi allerede har bestemt oss for å avvise.
        val vedlegg = listOf(vedlegg(ByteArray(MAKS_FILSTØRRELSE_BYTES + 1), Detect.APPLICATON_PDF))

        val feil = vedlegg.validerVedlegg().leftOrNull()!!

        feil.single().shouldBeInstanceOf<VedleggValideringsfeil.ForStortVedlegg>()
    }

    @Test
    fun `flere uavhengige feil rapporteres samlet`() {
        val vedlegg = listOf(
            pdfVedlegg(sidestørrelse = PDRectangle(14400f, 14400f)),
            vedlegg("%PDF-1.4\nødelagt".toByteArray(), Detect.APPLICATON_PDF),
        )

        val feil = vedlegg.validerVedlegg().leftOrNull()!!

        feil.map { it::class }.toSet() shouldBe setOf(
            VedleggValideringsfeil.PdfSideErForStor::class,
            VedleggValideringsfeil.PdfKunneIkkeLeses::class,
        )
    }

    // ---- Testdata ----

    private fun vedlegg(dokument: ByteArray, contentType: String, filnavn: String = "vedlegg.pdf") =
        Vedlegg(filnavn = filnavn, contentType = contentType, dokument = dokument)

    private fun bildeVedlegg(størrelse: Int, filnavn: String = "bilde.png") =
        vedlegg(ByteArray(størrelse), Detect.IMAGE_PNG, filnavn)

    private fun pdfVedlegg(antallSider: Int = 1, sidestørrelse: PDRectangle = PDRectangle.A4): Vedlegg =
        vedlegg(pdf(antallSider) { PDPage(sidestørrelse) }, Detect.APPLICATON_PDF)

    private fun pdf(antallSider: Int = 1, lagSide: () -> PDPage): ByteArray =
        PDDocument().use { dokument ->
            repeat(antallSider) { dokument.addPage(lagSide()) }
            val baos = ByteArrayOutputStream()
            dokument.save(baos)
            baos.toByteArray()
        }
}
