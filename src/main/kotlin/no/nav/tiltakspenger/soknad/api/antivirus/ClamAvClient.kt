package no.nav.tiltakspenger.soknad.api.antivirus

import arrow.core.Either
import arrow.core.Nel
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.kall.MultipartDel
import no.nav.tiltakspenger.libs.httpklient.infra.kall.tilMultipartDeler
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg
import java.net.URI
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Klient for å virusskanne vedlegg med ClamAV.
 *
 * Kildekode: ikke aktuelt — ClamAV tilbys som plattformtjeneste av Nais
 * Dokumentasjon: https://doc.nais.io/services/antivirus/
 * API-spec: - (ingen spec; REST-API-et er beskrevet i nais-dokumentasjonen)
 * Slack: #nais
 * Teamkatalog: ikke aktuelt (plattformtjeneste)
 *
 * Vedleggene lastes opp som `multipart/form-data` med én del per fil, akkurat som før migreringen.
 * Tar en [Nel] fordi en virusskanning uten filer er meningsløs; kallstedet har allerede tatt stilling til det tilfellet.
 * ClamAV kjører i klusteret uten autentisering, derfor [KlientAuth.Ingen].
 * Ingen retry, som før: en virusskanning av vedlegg på flere megabyte skal ikke gjentas automatisk.
 *
 * @param transport Det eneste stedet klienten rører nettverket; default er produksjonstransporten, tester sender inn `FakeHttpTransport`.
 */
class ClamAvClient(
    avEndpoint: String,
    clock: Clock,
    connectTimeout: Duration = 30.seconds,
    timeout: Duration = 30.seconds,
    transport: HttpTransport = JavaHttpTransport(connectTimeout = connectTimeout),
) : AvKlient {
    private val httpKlient: HttpKlient = HttpKlient(
        clock = clock,
        config = HttpKlientConfig(
            timeout = timeout,
            auth = KlientAuth.Ingen,
        ),
        transport = transport,
    )

    private val uri = URI.create(avEndpoint)

    override suspend fun scan(vedleggsListe: Nel<Vedlegg>): Either<HttpKlientError, List<AvSjekkResultat>> =
        httpKlient.postMultipart<List<AvSjekkResultat>>(
            uri = uri,
            // Feltnavnene er indeksbaserte, som før migreringen; MultipartDeler håndhever at de er unike.
            deler = vedleggsListe.mapIndexed { index, vedlegg ->
                MultipartDel(
                    feltnavn = "file$index",
                    filnavn = vedlegg.filnavn,
                    contentType = vedlegg.contentType,
                    innhold = vedlegg.dokument,
                )
            }.tilMultipartDeler(),
        ).map { it.body }
}

data class AvSjekkResultat(
    @JsonProperty("Filename") val filnavn: String,
    @JsonProperty("Result") val resultat: Status,
)

enum class Status {
    FOUND,
    OK,
    ERROR,
}
