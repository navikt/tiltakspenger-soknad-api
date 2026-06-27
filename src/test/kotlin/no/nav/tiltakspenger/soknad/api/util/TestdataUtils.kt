package no.nav.tiltakspenger.soknad.api.util

import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.libs.texas.client.TexasIntrospectionResponse
import no.nav.tiltakspenger.soknad.api.pdl.Navn
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
    )
}

fun getGyldigTexasIntrospectionResponse(
    fnr: String,
    acr: String,
) =
    TexasIntrospectionResponse(
        active = true,
        error = null,
        groups = null,
        roles = null,
        other = mapOf(
            "pid" to fnr,
            "acr" to acr,
        ),
    )

/**
 * Lager et lokalt test-token (usignert [PlainJWT]) med de oppgitte claims. Erstatter tidligere
 * bruk av mock-oauth2-server: tokenets signatur valideres ikke i testene fordi
 * texasClient.introspectToken er mocket. Vi trenger kun et serialiserbart token med claims man
 * kan lese tilbake (pid/acr) inn i det mockede introspeksjonssvaret.
 */
fun lagTestToken(claims: Map<String, String>): JWT {
    val builder = JWTClaimsSet.Builder()
    claims.forEach { (key, value) -> builder.claim(key, value) }
    return PlainJWT(builder.build())
}
