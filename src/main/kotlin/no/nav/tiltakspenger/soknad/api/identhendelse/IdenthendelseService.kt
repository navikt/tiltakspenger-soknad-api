package no.nav.tiltakspenger.soknad.api.identhendelse

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.soknad.api.soknad.SøknadRepo
import java.util.UUID

class IdenthendelseService(
    private val søknadRepo: SøknadRepo,
) {
    private val log = KotlinLogging.logger { }

    fun behandleIdenthendelse(id: UUID, identhendelseDto: IdenthendelseDto) {
        val gammeltFnr = Fnr.fromString(identhendelseDto.gammeltFnr)
        val nyttFnr = Fnr.fromString(identhendelseDto.nyttFnr)
        søknadRepo.oppdaterFnr(gammeltFnr = gammeltFnr, nyttFnr = nyttFnr)
        log.info { "Oppdatert fnr for identhendelse med id $id" }
    }
}
