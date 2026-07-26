package no.nav.tiltakspenger.soknad.api.pdl.client

import arrow.core.Either
import arrow.core.Nel
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata
import no.nav.tiltakspenger.libs.httpklient.HttpKlientResponse
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.kall.NavHeadere
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import no.nav.tiltakspenger.libs.texas.IdentityProvider
import no.nav.tiltakspenger.libs.texas.client.TexasClient
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkerRespons
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkersBarnRespons
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.hentBarnBolkQuery
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.hentPersonQuery
import java.net.URI
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Klient for å hente personopplysninger fra GraphQL-API-et til PDL (persondataløsningen).
 *
 * Kildekode: https://github.com/navikt/pdl
 * Dokumentasjon: https://pdl-docs.ansatt.nav.no/
 * API-spec: https://github.com/navikt/pdl/blob/15bdc571f0357f97f524dc496fb16217ff4aa94d/apps/api/src/main/resources/schemas/pdl.graphqls#L17 og https://pdl-playground.dev.intern.nav.no/ og https://pdl-pip-api.intern.dev.nav.no/swagger-ui/index.html (Swagger)
 * Slack: #pdl
 * Teamkatalog: https://teamkatalogen.nav.no/team/034cbcd2-ac28-4e2e-88c8-345945933f70
 *
 * Søkeroppslag går enten med brukerens eget token (TokenX on-behalf-of, vekslet i [fetchSøker]) eller med systemtoken ([fetchSøkerSystembruker] og [fetchBarn]).
 * Systemtokenet ligger på klienten som [KlientAuth.System]; OBO-tokenet sendes per kall og overstyrer det.
 *
 * Klienten logger ikke selv — feillogging skjer én gang i [no.nav.tiltakspenger.soknad.api.pdl.PdlService] via [KanIkkeHentePerson.logg].
 * Behandlingsnummeret B470 er tiltakspengers oppføring i behandlingskatalogen: https://behandlingskatalog.intern.nav.no/process/purpose/TILTAKSPENGER/7b1ef0b2-9d17-413e-8bc3-0efed8adc623
 *
 * @param endepunkt Hele URL-en til PDLs GraphQL-endepunkt.
 * @param transport Det eneste stedet klienten rører nettverket; default er produksjonstransporten, tester sender inn `FakeHttpTransport`.
 */
class PdlClient(
    endepunkt: String,
    clock: Clock,
    private val pdlScope: String,
    private val texasClient: TexasClient,
    authTokenProvider: AuthTokenProvider,
    connectTimeout: Duration = 10.seconds,
    timeout: Duration = 10.seconds,
    transport: HttpTransport = JavaHttpTransport(connectTimeout = connectTimeout),
) : PersonKlient {
    private val httpKlient: HttpKlient = HttpKlient(
        clock = clock,
        config = HttpKlientConfig(
            timeout = timeout,
            auth = KlientAuth.System(authTokenProvider),
        ),
        transport = transport,
    )

    private val uri = URI.create(endepunkt)

    private val headere = listOf(NavHeadere.tema("IND"), NavHeadere.behandlingsnummer("B470"))

    private val cache: Cache<String, SøkerRespons> = Caffeine.newBuilder()
        .expireAfterWrite(java.time.Duration.ofMinutes(60))
        .build()

    /** Tar en [Nel] fordi et bolkoppslag uten identer er meningsløst; kallstedet har allerede tatt stilling til det tilfellet. */
    override suspend fun fetchBarn(identer: Nel<String>): Either<KanIkkeHentePerson, SøkersBarnRespons> =
        httpKlient.postJson<GraphQLRespons<SøkersBarnRespons>>(
            uri = uri,
            body = hentBarnBolkQuery(identer),
            headere = headere,
            godta = Statusregel.Eksakt(200),
        ).tilData()

    /** Henter søker med brukerens eget token, vekslet til PDL via TokenX. */
    override suspend fun fetchSøker(
        fødselsnummer: String,
        subjectToken: String,
    ): Either<KanIkkeHentePerson, SøkerRespons> {
        cache.getIfPresent(fødselsnummer)?.let { return it.right() }
        val token = texasClient.exchangeToken(
            userToken = subjectToken,
            audienceTarget = pdlScope,
            identityProvider = IdentityProvider.TOKENX,
        )
        return fetchSøkerFraPdl(fødselsnummer = fødselsnummer, token = token)
    }

    /** Henter søker med systemtoken; brukes fra jobber der det ikke finnes en innlogget bruker. */
    override suspend fun fetchSøkerSystembruker(fødselsnummer: String): Either<KanIkkeHentePerson, SøkerRespons> {
        cache.getIfPresent(fødselsnummer)?.let { return it.right() }
        return fetchSøkerFraPdl(fødselsnummer = fødselsnummer, token = null)
    }

    /** [token] = `null` betyr at klientens systemtoken brukes. */
    private suspend fun fetchSøkerFraPdl(
        fødselsnummer: String,
        token: AccessToken?,
    ): Either<KanIkkeHentePerson, SøkerRespons> =
        httpKlient.postJson<GraphQLRespons<SøkerRespons>>(
            uri = uri,
            body = hentPersonQuery(fødselsnummer),
            headere = headere,
            bearerToken = token,
            godta = Statusregel.Eksakt(200),
        ).tilData().onRight { respons ->
            // Bare treff caches; et tomt oppslag skal kunne bli et treff senere uten å vente til cache-tiden løper ut.
            if (respons.hentPerson != null) cache.put(fødselsnummer, respons)
        }
}

/**
 * GraphQL-konvolutten fra PDL.
 * [data] er nullable fordi PDL svarer `200` med `data: null` når spørringen feiler funksjonelt.
 */
data class GraphQLRespons<T>(
    val data: T? = null,
    val errors: List<GraphQLFeilmelding>? = null,
)

data class GraphQLFeilmelding(
    val message: String,
    val extensions: GraphQLFeilExtensions? = null,
)

data class GraphQLFeilExtensions(
    val code: String? = null,
    val classification: String? = null,
)

/**
 * Mapper klientsvaret til domenefeil og pakker ut `data` fra GraphQL-konvolutten.
 * GraphQL svarer av design `200 OK` på alle svar, så funksjonelle feil må sjekkes på suksess-grenen.
 * Alle feil er fatale for kalleren, som før migreringen — PDL-feil har aldri gitt et delvis svar i denne appen.
 */
private fun <T : Any> Either<HttpKlientError, HttpKlientResponse<GraphQLRespons<T>>>.tilData(): Either<KanIkkeHentePerson, T> =
    mapLeft { KanIkkeHentePerson.KallFeilet(it) }.flatMap { respons ->
        respons.body.tilData(respons.metadata)
    }

private fun <T : Any> GraphQLRespons<T>.tilData(metadata: HttpKlientMetadata): Either<KanIkkeHentePerson, T> {
    val feilmeldinger = errors.orEmpty().map { it.message }.toNonEmptyListOrNull()
    if (feilmeldinger != null) {
        return KanIkkeHentePerson.GraphQLFeil(feilmeldinger = feilmeldinger, metadata = metadata).left()
    }
    return data?.right() ?: KanIkkeHentePerson.ResponsManglerData(metadata).left()
}
