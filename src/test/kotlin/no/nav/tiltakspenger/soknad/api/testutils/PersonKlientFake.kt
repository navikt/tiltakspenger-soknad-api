package no.nav.tiltakspenger.soknad.api.testutils

import arrow.atomic.Atomic
import arrow.atomic.update
import arrow.core.Either
import arrow.core.Nel
import arrow.core.right
import no.nav.tiltakspenger.libs.personklient.pdl.dto.PdlPersonBolkCode
import no.nav.tiltakspenger.soknad.api.pdl.client.KanIkkeHentePerson
import no.nav.tiltakspenger.soknad.api.pdl.client.PersonKlient
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.EndringsMetadata
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.FolkeregisterMetadata
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.ForelderBarnRelasjon
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.ForelderBarnRelasjonRolle
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.Fødsel
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.GeografiskTilknytning
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.Navn
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkerFraPDL
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkerRespons
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkersBarnFraPDL
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkersBarnFraPDLBolk
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkersBarnRespons
import java.time.Clock
import java.time.LocalDate

/**
 * Fake for [PersonKlient], med én voksen søker og barna som er registrert på hen.
 * Ved lokal kjøring svarer den likt for alle fødselsnumre; tester kan overstyre per person med [leggTilSøker] og [leggTilBarn].
 */
class PersonKlientFake(
    private val clock: Clock,
    /** Barnas fødselsnummer og fødselsdato, slik at barnetillegg kan fylles ut. */
    private val standardBarn: Map<String, LocalDate> = emptyMap(),
    private val standardFornavn: String = "Lokal",
    private val standardEtternavn: String = "Lokalsen",
) : PersonKlient {
    private val søkere = Atomic(emptyMap<String, SøkerRespons>())
    private val barn = Atomic(emptyMap<String, SøkersBarnFraPDLBolk>())

    private val søkerkall = Atomic(emptyList<String>())
    val antallSøkerkall: Int get() = søkerkall.get().size

    override suspend fun fetchSøker(
        fødselsnummer: String,
        subjectToken: String,
    ): Either<KanIkkeHentePerson, SøkerRespons> = hentSøker(fødselsnummer)

    override suspend fun fetchSøkerSystembruker(fødselsnummer: String): Either<KanIkkeHentePerson, SøkerRespons> =
        hentSøker(fødselsnummer)

    override suspend fun fetchBarn(identer: Nel<String>): Either<KanIkkeHentePerson, SøkersBarnRespons> =
        SøkersBarnRespons(hentPersonBolk = identer.map { ident -> barn.get()[ident] ?: barnIBolk(ident) }).right()

    /** Overstyrer svaret for én søker; kall den før testen ber om personalia. */
    fun leggTilSøker(fødselsnummer: String, respons: SøkerRespons) {
        søkere.update { it + (fødselsnummer to respons) }
    }

    /** Overstyrer svaret for ett barn i bolkoppslaget. */
    fun leggTilBarn(ident: String, bolk: SøkersBarnFraPDLBolk) {
        barn.update { it + (ident to bolk) }
    }

    private fun hentSøker(fødselsnummer: String): Either<KanIkkeHentePerson, SøkerRespons> {
        søkerkall.update { it + fødselsnummer }
        return (søkere.get()[fødselsnummer] ?: standardSøker()).right()
    }

    private fun standardSøker() = SøkerRespons(
        hentPerson = SøkerFraPDL(
            navn = listOf(navn(standardFornavn, standardEtternavn)),
            // Tom liste er PDLs måte å si «ugradert» på, jf. avklarGradering.
            adressebeskyttelse = emptyList(),
            foedselsdato = listOf(Fødsel(LocalDate.now(clock).minusYears(35), folkeregistermetadata, metadata)),
            forelderBarnRelasjon = standardBarn.keys.map { ident ->
                ForelderBarnRelasjon(ident, ForelderBarnRelasjonRolle.BARN, folkeregistermetadata, metadata)
            },
            doedsfall = emptyList(),
        ),
        hentGeografiskTilknytning = GeografiskTilknytning("KOMMUNE", "1122", null, null),
    )

    private fun barnIBolk(ident: String) = SøkersBarnFraPDLBolk(
        ident = ident,
        person = SøkersBarnFraPDL(
            navn = listOf(navn("Barn$ident".take(9), "Barnesen")),
            adressebeskyttelse = emptyList(),
            foedselsdato = listOf(Fødsel(standardBarn[ident] ?: LocalDate.now(clock).minusYears(8), folkeregistermetadata, metadata)),
            doedsfall = emptyList(),
        ),
        code = PdlPersonBolkCode.OK,
    )

    private fun navn(fornavn: String, etternavn: String) =
        Navn(fornavn = fornavn, etternavn = etternavn, metadata = metadata, folkeregistermetadata = folkeregistermetadata)

    private companion object {
        /** PDL er kilden, og vi har ingen endringshistorikk å øve på i en fake. */
        val metadata = EndringsMetadata(endringer = emptyList(), master = "FREG")

        val folkeregistermetadata = FolkeregisterMetadata(
            aarsak = null,
            ajourholdstidspunkt = null,
            gyldighetstidspunkt = null,
            kilde = null,
            opphoerstidspunkt = null,
            sekvens = null,
        )
    }
}
