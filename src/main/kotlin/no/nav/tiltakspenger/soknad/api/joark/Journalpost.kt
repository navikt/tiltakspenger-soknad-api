package no.nav.tiltakspenger.soknad.api.joark

import no.nav.tiltakspenger.soknad.api.domain.Søknad
import no.nav.tiltakspenger.soknad.api.objectMapper
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import java.util.Base64

enum class Tema(val value: String) {
    TILTAKSPENGER("IND"),
}

enum class Behandlingstema(val value: String) {
    TILTAKSPENGER(""),
}

sealed class Journalpost {
    val tema: String = Tema.TILTAKSPENGER.value
    val behandlingstema: String = Behandlingstema.TILTAKSPENGER.value
    abstract val journalfoerendeEnhet: String?
    abstract val tittel: String
    abstract val journalpostType: JournalPostType
    abstract val kanal: String?
    abstract val avsenderMottaker: AvsenderMottaker
    abstract val bruker: Bruker
    abstract val sak: Sak?
    abstract val dokumenter: List<JournalpostDokument>

    data class Søknadspost private constructor(
        val fnr: String,
        override val dokumenter: List<JournalpostDokument>,
    ) : Journalpost() {
        override val tittel: String = SØKNADSPOSTTITTEL
        override val avsenderMottaker: AvsenderMottaker = AvsenderMottaker(id = fnr)
        override val bruker: Bruker = Bruker(id = fnr)
        override val sak: Sak? = null
        override val journalpostType: JournalPostType = JournalPostType.INNGAAENDE
        override val kanal: String = "NAV_NO"
        override val journalfoerendeEnhet: String? = null

        companion object {
            private const val SØKNADSPOSTTITTEL = "Søknad om tiltakspenger" // TODO Sjekk at tittel er ok
            private const val BREVKODE_FOR_SØKNAD = "NAV 76-13.45"
            fun from(
                fnr: String,
                søknad: Søknad,
                pdf: ByteArray,
                vedlegg: List<Vedlegg>,
            ) =
                Søknadspost(
                    fnr = fnr,
                    dokumenter = mutableListOf(
                        lagHoveddokument(
                            pdf = pdf,
                            søknad = søknad,
                        ),
                    ).apply {
                        this.addAll(lagVedleggsdokumenter(vedlegg))
                    },
                )

            private fun lagHoveddokument(pdf: ByteArray, søknad: Søknad): JournalpostDokument =
                JournalpostDokument(
                    tittel = SØKNADSPOSTTITTEL,
                    // dokumentKategori = DokumentKategori.SOK,
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
    }
}

internal data class JournalpostRequest(
    val tittel: String,
    val journalpostType: JournalPostType,
    val tema: String,
    val kanal: String?,
    val behandlingstema: String,
    // val journalfoerendeEnhet: String,
    val avsenderMottaker: AvsenderMottaker,
    val bruker: Bruker,
    // val sak: Sak,
    val dokumenter: List<JournalpostDokument>,
    val eksternReferanseId: String,
)

data class AvsenderMottaker(
    val id: String,
    val idType: String = "FNR",
)

data class Bruker(
    val id: String,
    val idType: String = "FNR",
)

sealed class Sak {
    data class Fagsak(
        val fagsakId: String,
        val fagsaksystem: String = "IND",
        val sakstype: String = "FAGSAK",
    ) : Sak()

    data class GenerellSak(
        val sakstype: String = "GENERELL_SAK",
    ) : Sak()
}

data class JournalpostDokument(
    val tittel: String,
    // val dokumentKategori: DokumentKategori,
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

enum class DokumentKategori(val type: String) {
    SOK("SOK"),
}
