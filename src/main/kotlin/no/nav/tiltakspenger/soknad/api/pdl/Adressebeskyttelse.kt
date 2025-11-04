package no.nav.tiltakspenger.soknad.api.pdl

import no.nav.tiltakspenger.soknad.api.pdl.client.dto.Changeable
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.EndringsMetadata
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.FolkeregisterMetadata
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.getEndringstidspunktOrNull
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.kildeErUdokumentert
import no.nav.tiltakspenger.soknad.api.pdl.routes.dto.AdressebeskyttelseDTO

enum class AdressebeskyttelseGradering {
    STRENGT_FORTROLIG_UTLAND,
    STRENGT_FORTROLIG,
    FORTROLIG,
    UGRADERT,
    ;

    fun toDTO(): AdressebeskyttelseDTO = when (this) {
        STRENGT_FORTROLIG_UTLAND -> AdressebeskyttelseDTO.STRENGT_FORTROLIG_UTLAND
        STRENGT_FORTROLIG -> AdressebeskyttelseDTO.STRENGT_FORTROLIG
        FORTROLIG -> AdressebeskyttelseDTO.FORTROLIG
        UGRADERT -> AdressebeskyttelseDTO.UGRADERT
    }
}

data class Adressebeskyttelse(
    val gradering: AdressebeskyttelseGradering,
    override val folkeregistermetadata: FolkeregisterMetadata? = null,
    override val metadata: EndringsMetadata,
) : Changeable

fun avklarGradering(gradering: List<Adressebeskyttelse>): AdressebeskyttelseGradering {
    return if (gradering.isEmpty()) {
        AdressebeskyttelseGradering.UGRADERT
    } else if (gradering.size == 1) {
        gradering.firstOrNull { !kildeErUdokumentert(it.metadata) }?.gradering
            ?: throw IllegalStateException("Adressebeskyttelse kunne ikke avklares")
    } else {
        gradering
            .sortedByDescending { getEndringstidspunktOrNull(it) }
            .firstOrNull { it.gradering != AdressebeskyttelseGradering.UGRADERT && !kildeErUdokumentert(it.metadata) }?.gradering
            ?: throw IllegalStateException("Adressebeskyttelse kunne ikke avklares")
    }
}
