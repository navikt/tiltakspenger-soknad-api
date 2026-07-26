package no.nav.tiltakspenger.soknad.api.vedlegg

import io.ktor.server.plugins.requestvalidation.RequestValidationException

/**
 * Maks størrelse på ett enkelt vedlegg, i bytes.
 * Speiler grensen i søknadsdialogen (tiltakspenger-soknad), og ligger under body-grensen på 10 MiB i tiltakspenger-pdfgenrs, som konverterer vedleggene til PDF.
 */
const val MAKS_FILSTØRRELSE_BYTES = 10_000_000

/**
 * Maks antall vedlegg for hele søknaden.
 * Speiler grensen i søknadsdialogen (tiltakspenger-soknad).
 */
const val MAKS_ANTALL_VEDLEGG = 25

/**
 * Valideringen i søknadsdialogen er ikke en sikkerhetsgrense — endepunktet tar imot multipart fra alle med et gyldig token.
 * Uten denne sjekken oppdages for store eller for mange vedlegg først i journalføringsjobben, der brukeren er borte og søknaden allerede er lagret.
 * Kaster [RequestValidationException] slik at ruta svarer 400 på samme måte som ved besvarelser som ikke validerer.
 */
fun List<Vedlegg>.validerVedlegg() {
    val feilmeldinger = mutableListOf<String>()
    if (size > MAKS_ANTALL_VEDLEGG) {
        feilmeldinger.add("Søknaden har $size vedlegg, men kan ha maks $MAKS_ANTALL_VEDLEGG.")
    }
    val antallForStore = count { it.dokument.size > MAKS_FILSTØRRELSE_BYTES }
    if (antallForStore > 0) {
        feilmeldinger.add("$antallForStore av vedleggene er større enn maksgrensen på $MAKS_FILSTØRRELSE_BYTES bytes.")
    }
    if (feilmeldinger.isNotEmpty()) {
        // Filnavn kan inneholde personopplysninger, så feilen beskriver bare antall — den havner i vanlig logg.
        throw RequestValidationException("Vedleggene i søknaden", feilmeldinger)
    }
}
