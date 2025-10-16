package no.nav.tiltakspenger.soknad.api.soknad

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.tiltakspenger.soknad.api.objectMapper

class FylkeService {
    private val geografiKodeverk = requireNotNull(NySøknadService::class.java.getResource("/geografikodeverk.json")).readText()
    private val geografi = objectMapper.readValue<Geografi>(geografiKodeverk)

    fun brukersFylkeRutesTilTpsak(geografiskTilknytning: String?, fylkerSomRutesTilTpsak: List<String>): Boolean {
        if (fylkerSomRutesTilTpsak.isEmpty()) {
            return false
        }
        geografiskTilknytning?.let {
            val brukersFylkePath = findPathToGeografiskTilknytning(it) ?: return false
            return brukersFylkePath.any { fylkerSomRutesTilTpsak.contains(it) }
        }
        return false
    }

    fun findPathToGeografiskTilknytning(geografiskTilknytning: String): List<String>? {
        fun search(node: Hierarkinode, path: List<String>): List<String>? {
            val newPath = path + node.kode
            if (node.kode == geografiskTilknytning) return newPath
            return node.undernoder?.values?.firstNotNullOfOrNull { search(it, newPath) }
        }

        return geografi.noder.values.firstNotNullOfOrNull { search(it, emptyList()) }
    }
}
