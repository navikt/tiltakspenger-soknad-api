package no.nav.tiltakspenger.soknad.api.vedlegg

import arrow.core.Either
import arrow.core.Nel
import arrow.core.left
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import no.nav.tiltakspenger.soknad.api.util.Detect
import no.nav.tiltakspenger.soknad.api.util.PdfTools

/**
 * Maks størrelse på ett enkelt vedlegg, i bytes.
 * Speiler grensen i søknadsdialogen (tiltakspenger-soknad), og ligger under body-grensen på 10 MiB i tiltakspenger-pdfgenrs, som konverterer vedleggene til PDF.
 */
const val MAKS_FILSTØRRELSE_BYTES = 10_000_000

/**
 * Maks antall vedlegg for hele søknaden.
 * Speiler grensen i søknadsdialogen (tiltakspenger-soknad).
 *
 * Grensen gjelder søknaden, ikke det enkelte barnet.
 * Vedleggene er knyttet til hvert barn i søknadsdialogen, men den koblingen sendes ikke hit — `lagFormDataForInnsending` legger dem på som `vedlegg-0`, `vedlegg-1` osv., så det vi mottar er en flat liste.
 */
const val MAKS_ANTALL_VEDLEGG = 10

/**
 * Maks samlet størrelse på alle vedleggene i søknaden.
 * Per fil-grensen alene er ikke nok: maks antall vedlegg à maks filstørrelse ville blitt en multipart-body vi materialiserer i minnet i sin helhet.
 */
const val MAKS_TOTAL_FILSTØRRELSE_BYTES = 50_000_000

/**
 * Maks antall piksler i én PDF-side, målt på sidens geometri slik den vil bli rendret.
 *
 * Dette er en sikkerhetsgrense, ikke en bruksgrense.
 * `PDFRenderer` allokerer en `BufferedImage` som følger sidens cropBox og ikke filstørrelsen, så en PDF på under 600 bytes kan be om nesten 800 MiB heap — mer enn poden har.
 * 20 megapiksler er rundt 40 ganger en A4-side på 72 dpi og dekker selv store plansjeformater med god margin, men kapper en enkelt side til omtrent 80 MiB.
 */
const val MAKS_PIKSLER_PER_PDF_SIDE = 20_000_000L

/**
 * Maks antall sider i et PDF-vedlegg.
 *
 * Også dette er en sikkerhetsgrense: hver side blir et eget bilde og et eget kall mot pdfgenrs, og `PdfTools.konverterPdfTilBilder` bygger samtlige sider i minnet før det første kallet.
 * En tom PDF-side er rundt ti bytes, så filstørrelsen alene stanser ingenting — en 10 MB PDF har plass til over en million sider.
 * Tallet bør bekreftes av fagsiden; det er satt for å holde både heap og antall utgående kall nede, ikke ut fra hva en søker realistisk trenger å sende inn.
 */
const val MAKS_SIDER_I_PDF = 100

/**
 * Hvorfor vedleggene i en søknad ble avvist.
 *
 * [melding] er formulert for å kunne vises til brukeren og logges i vanlig logg, og nevner derfor aldri filnavn — brukeren velger dem selv, og de kan inneholde personopplysninger.
 */
sealed interface VedleggValideringsfeil {
    val melding: String

    data class ForMangeVedlegg(val antall: Int) : VedleggValideringsfeil {
        override val melding = "Søknaden har $antall vedlegg, men kan ha maks $MAKS_ANTALL_VEDLEGG."
    }

    data class ForStortVedlegg(val antall: Int) : VedleggValideringsfeil {
        override val melding = "$antall av vedleggene er større enn maksgrensen på $MAKS_FILSTØRRELSE_BYTES bytes."
    }

    data class ForStorSamletStørrelse(val samletStørrelse: Long) : VedleggValideringsfeil {
        override val melding =
            "Vedleggene er til sammen $samletStørrelse bytes, men kan til sammen være maks $MAKS_TOTAL_FILSTØRRELSE_BYTES bytes."
    }

    data class PdfHarForMangeSider(val antallSider: Int) : VedleggValideringsfeil {
        override val melding = "Et av vedleggene har $antallSider sider, men kan ha maks $MAKS_SIDER_I_PDF."
    }

    data class PdfSideErForStor(val størsteSideIPiksler: Long) : VedleggValideringsfeil {
        override val melding =
            "Et av vedleggene har en side som er for stor til å behandles ($størsteSideIPiksler piksler, maks $MAKS_PIKSLER_PER_PDF_SIDE)."
    }

    data object PdfKunneIkkeLeses : VedleggValideringsfeil {
        override val melding = "Et av vedleggene er ikke en lesbar PDF-fil."
    }
}

/**
 * Validerer vedleggene før søknaden lagres.
 *
 * Valideringen i søknadsdialogen er ikke en sikkerhetsgrense — endepunktet tar imot multipart fra alle med et gyldig token.
 * Uten denne sjekken oppdages problemene først i journalføringsjobben, der brukeren er borte, søknaden allerede er lagret, og en enkelt fil kan stoppe journalføringen for alle andre.
 *
 * Rekkefølgen er bevisst: er noe for stort eller for mange, returnerer vi uten å la pdfbox røre en eneste fil.
 * Vi skal ikke parse innhold vi uansett har bestemt oss for å avvise.
 */
fun List<Vedlegg>.validerVedlegg(): Either<Nel<VedleggValideringsfeil>, Unit> {
    validerStørrelseOgAntall().toNonEmptyListOrNull()?.let { return it.left() }
    return validerPdfer().toNonEmptyListOrNull()?.left() ?: Unit.right()
}

private fun List<Vedlegg>.validerStørrelseOgAntall(): List<VedleggValideringsfeil> = listOfNotNull(
    VedleggValideringsfeil.ForMangeVedlegg(size).takeIf { size > MAKS_ANTALL_VEDLEGG },
    count { it.dokument.size > MAKS_FILSTØRRELSE_BYTES }
        .takeIf { it > 0 }
        ?.let { VedleggValideringsfeil.ForStortVedlegg(it) },
    // Summeres som Long: en request som sprenger grensen grovt ville ellers kunne overflowe Int og se lovlig ut.
    sumOf { it.dokument.size.toLong() }
        .takeIf { it > MAKS_TOTAL_FILSTØRRELSE_BYTES }
        ?.let { VedleggValideringsfeil.ForStorSamletStørrelse(it) },
)

private fun List<Vedlegg>.validerPdfer(): List<VedleggValideringsfeil> =
    filter { it.contentType == Detect.APPLICATON_PDF }.mapNotNull { it.validerPdf() }

/**
 * Leser sidene i PDF-en for å se om vi trygt kan rendre den senere.
 * `Either.catch` er grensen mot pdfbox, som kaster på alt fra avkortede filer til ødelagte kryssreferansetabeller.
 */
private fun Vedlegg.validerPdf(): VedleggValideringsfeil? =
    Either.catch { PdfTools.pikslerPerSide(dokument) }.fold(
        { VedleggValideringsfeil.PdfKunneIkkeLeses },
        { sider ->
            when {
                sider.size > MAKS_SIDER_I_PDF -> VedleggValideringsfeil.PdfHarForMangeSider(sider.size)

                else -> sider.max().takeIf { it > MAKS_PIKSLER_PER_PDF_SIDE }
                    ?.let { VedleggValideringsfeil.PdfSideErForStor(it) }
            }
        },
    )
