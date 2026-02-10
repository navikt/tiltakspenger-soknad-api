package no.nav.tiltakspenger.soknad.api.soknad.routes

import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.soknad.api.soknad.SpørsmålsbesvarelserDTO
import no.nav.tiltakspenger.soknad.api.soknad.validerRequest
import no.nav.tiltakspenger.soknad.api.util.sjekkContentType
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

suspend fun taInnSøknadSomMultipart(søknadSomMultipart: MultiPartData): Pair<SpørsmålsbesvarelserDTO, List<Vedlegg>> {
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
