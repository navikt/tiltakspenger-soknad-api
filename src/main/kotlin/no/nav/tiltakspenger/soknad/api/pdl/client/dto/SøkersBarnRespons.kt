package no.nav.tiltakspenger.soknad.api.pdl.client.dto

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.libs.personklient.pdl.dto.PdlPersonBolkCode
import no.nav.tiltakspenger.soknad.api.pdl.Adressebeskyttelse
import no.nav.tiltakspenger.soknad.api.pdl.Person
import no.nav.tiltakspenger.soknad.api.pdl.avklarGradering

data class SøkersBarnFraPDL(
    val navn: List<Navn>,
    val adressebeskyttelse: List<Adressebeskyttelse>,
    val foedselsdato: List<Fødsel>,
    val doedsfall: List<Dødsfall>,
)

data class SøkersBarnFraPDLBolk(
    val ident: String,
    val person: SøkersBarnFraPDL?,
    val code: PdlPersonBolkCode,
)

private val logger = KotlinLogging.logger { }

data class SøkersBarnRespons(
    val hentPersonBolk: List<SøkersBarnFraPDLBolk>,
) {
    fun toPersoner(): List<Person?> {
        return hentPersonBolk.map {
            val person = it.person
            if (person == null) {
                logger.error { "Feil ved henting av barn med bolkoppslag fra PDL, code=${it.code}" }
                Sikkerlogg.error { "Feil ved henting av barn med bolkoppslag fra PDL, code=${it.code}, ident=${it.ident}" }
                return@map null
            }
            val navn = avklarNavn(person.navn)
            val fødsel = avklarFødsel(person.foedselsdato)
            val dødsfall = person.doedsfall.isNotEmpty()
            val adressebeskyttelseGradering = avklarGradering(person.adressebeskyttelse)
            return@map Person(
                fnr = Fnr.fromString(it.ident),
                fornavn = navn.fornavn,
                mellomnavn = navn.mellomnavn,
                etternavn = navn.etternavn,
                adressebeskyttelseGradering = adressebeskyttelseGradering,
                fødselsdato = fødsel.foedselsdato,
                erDød = dødsfall,
                geografiskTilknytning = null,
            )
        }
    }
}
