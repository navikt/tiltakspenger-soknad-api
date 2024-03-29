package no.nav.tiltakspenger.soknad.api.soknad

import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import mu.KotlinLogging
import no.nav.tiltakspenger.soknad.api.deserialize
import no.nav.tiltakspenger.soknad.api.domain.SøknadDTO
import no.nav.tiltakspenger.soknad.api.joark.JoarkService
import no.nav.tiltakspenger.soknad.api.pdf.PdfService
import no.nav.tiltakspenger.soknad.api.pdl.PersonDTO
import no.nav.tiltakspenger.soknad.api.util.sjekkContentType
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import java.time.LocalDateTime

val log = KotlinLogging.logger { }

class SøknadServiceImpl(
    private val pdfService: PdfService,
    private val joarkService: JoarkService,
) : SøknadService {
    override suspend fun opprettDokumenterOgArkiverIJoark(
        spørsmålsbesvarelser: SpørsmålsbesvarelserDTO,
        fnr: String,
        person: PersonDTO,
        vedlegg: List<Vedlegg>,
        acr: String,
        innsendingTidspunkt: LocalDateTime,
        callId: String,
    ): String {
        val vedleggsnavn = vedlegg.stream().map { it.filnavn }.toList()
        val søknadDTO = SøknadDTO.toDTO(
            spørsmålsbesvarelser = spørsmålsbesvarelser,
            fnr = fnr,
            person = person,
            acr = acr,
            innsendingTidspunkt = innsendingTidspunkt,
            vedleggsnavn = vedleggsnavn,
        )
        val pdf = pdfService.lagPdf(søknadDTO)
        log.info { "Generering av søknadsPDF OK" }
        val vedleggSomPdfer = pdfService.konverterVedlegg(vedlegg)
        log.info { "Vedleggskonvertering OK" }
        return joarkService.sendPdfTilJoark(pdf = pdf, søknadDTO = søknadDTO, fnr = fnr, vedlegg = vedleggSomPdfer, callId = callId)
    }

    override suspend fun taInnSøknadSomMultipart(søknadSomMultipart: MultiPartData): Pair<SpørsmålsbesvarelserDTO, List<Vedlegg>> {
        lateinit var spørsmålsbesvarelserDTO: SpørsmålsbesvarelserDTO
        val vedleggListe = mutableListOf<Vedlegg>()
        søknadSomMultipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    spørsmålsbesvarelserDTO = part.toSpørsmålsbesvarelser()
                }

                is PartData.FileItem -> {
                    vedleggListe.add(part.toVedlegg())
                }

                else -> {}
            }
            part.dispose()
        }

        return Pair(spørsmålsbesvarelserDTO, vedleggListe)
    }
}

fun PartData.FileItem.toVedlegg(): Vedlegg {
    val filnavn = this.originalFileName ?: "untitled-${this.hashCode()}"
    val fileBytes = this.streamProvider().readBytes()
    return Vedlegg(filnavn = filnavn, contentType = sjekkContentType(fileBytes), dokument = fileBytes)
}

fun PartData.FormItem.toSpørsmålsbesvarelser(): SpørsmålsbesvarelserDTO {
    if (this.name == "søknad") {
        return deserialize<SpørsmålsbesvarelserDTO>(this.value).validerRequest()
    }
    throw UnrecognizedFormItemException(message = "Recieved multipart form with unknown key ${this.name}")
}

class UnrecognizedFormItemException(message: String) : RuntimeException(message)
class MissingContentException(message: String) : RuntimeException(message)
