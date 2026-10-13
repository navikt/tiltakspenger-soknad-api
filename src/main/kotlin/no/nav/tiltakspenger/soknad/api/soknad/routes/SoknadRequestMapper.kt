package no.nav.tiltakspenger.soknad.api.soknad.routes

import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.plugins.BadRequestException
import io.ktor.utils.io.toByteArray
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.soknad.api.soknad.SpørsmålsbesvarelserDTO
import no.nav.tiltakspenger.soknad.api.soknad.validerRequest
import no.nav.tiltakspenger.soknad.api.util.sjekkContentType
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import tools.jackson.core.JacksonException
import java.time.Clock

suspend fun taInnSøknadSomMultipart(søknadSomMultipart: MultiPartData, clock: Clock): Pair<SpørsmålsbesvarelserDTO, List<Vedlegg>> {
    lateinit var spørsmålsbesvarelserDTO: SpørsmålsbesvarelserDTO
    val vedleggListe = mutableListOf<Vedlegg>()
    søknadSomMultipart.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> {
                spørsmålsbesvarelserDTO = part.toSpørsmålsbesvarelser(clock)
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

/**
 * Leser vedlegget med den suspenderende [PartData.FileItem.provider], ikke den utgåtte `streamProvider`.
 * Den siste gir en blokkerende `InputStream` som venter på bytes fra Nettys event loop — og siden det er den samme tråden som skal levere dem, står kallet i vranglås til klienten gir opp.
 */
suspend fun PartData.FileItem.toVedlegg(): Vedlegg {
    val filnavn = this.originalFileName ?: "untitled-${this.hashCode()}"
    val fileBytes = this.provider().toByteArray()
    return Vedlegg(filnavn = filnavn, contentType = sjekkContentType(fileBytes), dokument = fileBytes)
}

/**
 * JSON som ikke lar seg lese som en søknad, f.eks. et barn uten svar på oppholdssted, er en ugyldig forespørsel og skal gi 400.
 */
fun PartData.FormItem.toSpørsmålsbesvarelser(clock: Clock): SpørsmålsbesvarelserDTO {
    if (this.name == "søknad") {
        val søknad = try {
            deserialize<SpørsmålsbesvarelserDTO>(this.value)
        } catch (e: JacksonException) {
            throw BadRequestException("Kunne ikke lese søknaden", e)
        }
        return søknad.validerRequest(clock)
    }
    throw UnrecognizedFormItemException(message = "Recieved multipart form with unknown key ${this.name}")
}

class UnrecognizedFormItemException(message: String) : RuntimeException(message)
class MissingContentException(message: String) : RuntimeException(message)
