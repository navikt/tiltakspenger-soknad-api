package no.nav.tiltakspenger.soknad.api.dokarkiv

import no.nav.tiltakspenger.libs.json.objectMapper
import no.nav.tiltakspenger.soknad.api.domain.Søknad
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import java.util.Base64

enum class Tema(val value: String) {
    TILTAKSPENGER("IND"),
}

const val JOURNALFORENDE_ENHET_AUTOMATISK_BEHANDLING = "9999"
private const val TITTEL = "Søknad om tiltakspenger"
private const val BREVKODE_FOR_SØKNAD = "NAV 76-13.45"

data class JournalpostRequest private constructor(
    val tittel: String = TITTEL,
    val journalpostType: JournalPostType = JournalPostType.INNGAAENDE,
    val tema: String = Tema.TILTAKSPENGER.value,
    val kanal: String = "NAV_NO",
    val journalfoerendeEnhet: String?,
    val avsenderMottaker: AvsenderMottaker,
    val bruker: Bruker,
    val sak: Sak?,
    val dokumenter: List<JournalpostDokument>,
    val eksternReferanseId: String,
) {
    companion object {
        fun from(
            fnr: String,
            søknad: Søknad,
            pdf: ByteArray,
            vedlegg: List<Vedlegg>,
            saksnummer: String?,
        ) = JournalpostRequest(
            journalfoerendeEnhet = saksnummer?.let { JOURNALFORENDE_ENHET_AUTOMATISK_BEHANDLING },
            avsenderMottaker = AvsenderMottaker(id = fnr),
            bruker = Bruker(id = fnr),
            sak = saksnummer?.let {
                Sak(fagsakId = it)
            },
            dokumenter = mutableListOf(
                lagHoveddokument(
                    pdf = pdf,
                    søknad = søknad,
                ),
            ).apply {
                this.addAll(lagVedleggsdokumenter(vedlegg))
            },
            eksternReferanseId = søknad.id,
        )

        private fun lagHoveddokument(pdf: ByteArray, søknad: Søknad): JournalpostDokument =
            JournalpostDokument(
                tittel = TITTEL,
                brevkode = BREVKODE_FOR_SØKNAD,
                dokumentvarianter = listOf(
                    DokumentVariant.ArkivPDF(fysiskDokument = Base64.getEncoder().encodeToString(pdf)),
                    DokumentVariant.OriginalJson(
                        fysiskDokument = Base64.getEncoder()
                            .encodeToString(objectMapper.writeValueAsString(søknad).toByteArray()),
                    ),
                ),
            )

        private fun lagVedleggsdokumenter(vedleggListe: List<Vedlegg>): List<JournalpostDokument> =
            vedleggListe.map { vedlegg ->
                JournalpostDokument(
                    tittel = vedlegg.filnavn,
                    brevkode = vedlegg.brevkode,
                    dokumentvarianter = listOf(
                        DokumentVariant.VedleggPDF(
                            fysiskDokument = Base64.getEncoder().encodeToString(vedlegg.dokument),
                            filnavn = vedlegg.filnavn,
                        ),
                    ),
                )
            }
    }

    fun kanFerdigstilleAutomatisk() =
        !journalfoerendeEnhet.isNullOrEmpty() && !sak?.fagsakId.isNullOrEmpty()
}

data class AvsenderMottaker(
    val id: String,
    val idType: String = "FNR",
)

data class Bruker(
    val id: String,
    val idType: String = "FNR",
)

data class Sak(
    val fagsakId: String,
    val fagsaksystem: String = "TILTAKSPENGER",
    val sakstype: String = "FAGSAK",
)

data class JournalpostDokument(
    val tittel: String,
    val brevkode: String?,
    val dokumentvarianter: List<DokumentVariant>,
)

sealed class DokumentVariant {
    abstract val filtype: String
    abstract val fysiskDokument: String
    abstract val variantformat: String
    abstract val filnavn: String

    data class ArkivPDF(
        override val fysiskDokument: String,
    ) : DokumentVariant() {
        override val filtype: String = "PDFA"
        override val variantformat: String = "ARKIV"
        override val filnavn: String = "tiltakspengersoknad.pdf"
    }

    data class VedleggPDF(
        override val fysiskDokument: String,
        override val filnavn: String,
    ) : DokumentVariant() {
        override val filtype: String = "PDFA"
        override val variantformat: String = "ARKIV"
    }

    data class OriginalJson(
        override val fysiskDokument: String,
    ) : DokumentVariant() {
        override val filtype: String = "JSON"
        override val variantformat: String = "ORIGINAL"
        override val filnavn: String = "tiltakspengersoknad.json"
    }
}

enum class JournalPostType(val type: String) {
    INNGAAENDE("INNGAAENDE"),
    UTGAAENDE("UTGAAENDE"),
}
