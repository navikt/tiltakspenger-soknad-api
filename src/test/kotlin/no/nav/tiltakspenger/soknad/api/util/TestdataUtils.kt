package no.nav.tiltakspenger.soknad.api.util

import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.libs.personklient.pdl.dto.EndringsMetadata
import no.nav.tiltakspenger.libs.personklient.pdl.dto.FolkeregisterMetadata
import no.nav.tiltakspenger.libs.personklient.pdl.dto.Navn
import no.nav.tiltakspenger.libs.texas.client.TexasIntrospectionResponse
import no.nav.tiltakspenger.soknad.api.soknad.Applikasjonseier
import no.nav.tiltakspenger.soknad.api.soknad.MottattSøknad
import no.nav.tiltakspenger.soknad.api.soknad.SpørsmålsbesvarelserDTO
import no.nav.tiltakspenger.soknad.api.soknad.validering.spørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import java.time.LocalDateTime

fun genererMottattSøknadForTest(
    id: SøknadId = SøknadId.random(),
    søknadSpm: SpørsmålsbesvarelserDTO = spørsmålsbesvarelser(),
    fnr: String = "12345678901",
    opprettet: LocalDateTime = LocalDateTime.now(),
    vedlegg: List<Vedlegg> = listOf(
        Vedlegg(
            filnavn = "filnavn",
            contentType = "pdf",
            dokument = ByteArray(1),
            brevkode = "123",
        ),
    ),
    versjon: String = "1",
    acr: String = "acr",
    eier: Applikasjonseier,
    saksnummer: String? = null,
) = MottattSøknad(
    id = id,
    versjon = versjon,
    søknad = null,
    søknadSpm = søknadSpm,
    vedlegg = vedlegg,
    fnr = fnr,
    acr = acr,
    fornavn = null,
    etternavn = null,
    sendtTilVedtak = null,
    journalført = null,
    journalpostId = null,
    opprettet = opprettet,
    eier = eier,
    saksnummer = saksnummer,
)

fun getTestNavnFraPdl(): Navn {
    return Navn(
        fornavn = "Fornavn",
        mellomnavn = "Mellomnavn",
        etternavn = "Etternavn",
        metadata = EndringsMetadata(
            endringer = emptyList(),
            master = "FREG",
        ),
        folkeregistermetadata = FolkeregisterMetadata(
            aarsak = null,
            ajourholdstidspunkt = null,
            gyldighetstidspunkt = null,
            kilde = null,
            opphoerstidspunkt = null,
            sekvens = null,
        ),
    )
}

fun getGyldigTexasIntrospectionResponse(
    fnr: String,
    acr: String,
) =
    TexasIntrospectionResponse(
        active = true,
        error = null,
        other = mapOf(
            "pid" to fnr,
            "acr" to acr,
        ),
    )
