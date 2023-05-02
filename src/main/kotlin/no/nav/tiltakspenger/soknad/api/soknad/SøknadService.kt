package no.nav.tiltakspenger.soknad.api.soknad

import io.ktor.http.content.MultiPartData
import no.nav.tiltakspenger.soknad.api.pdl.PersonDTO
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

interface SøknadService {
    suspend fun opprettDokumenterOgArkiverIJoark(søknad: SpørsmålsbesvarelserDTO, fnr: String, person: PersonDTO, vedlegg: List<Vedlegg>, acr: String): String
    suspend fun taInnSøknadSomMultipart(søknadSomMultipart: MultiPartData): Pair<SpørsmålsbesvarelserDTO, List<Vedlegg>>
}
