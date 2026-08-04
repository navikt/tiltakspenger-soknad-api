package no.nav.tiltakspenger.soknad.api.tiltak.skygge

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientResponse
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.libs.tiltak.TiltakshistorikkDTO
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakshistorikk
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.Identoppslag
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.KunneIkkeHenteTiltakshistorikk
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.TiltakshistorikkHenter
import no.nav.tiltakspenger.soknad.api.metrics.MetricsCollector
import java.time.Clock
import java.time.LocalDate

/**
 * Skyggekjøring av tiltaksdeltakelse-modulen ved siden av dagens vei om tiltakspenger-tiltak.
 *
 * Hensikten er å måle den nye veien mot den gamle på ekte data før vi bytter, uten at bruker merker noe.
 * Derfor gjelder tre regler uten unntak: returverdien til søknaden røres aldri, sammenligningen er ren sideeffekt, og skyggen får ikke påvirke svartiden.
 * Ny vei har et verstefallsbudsjett på rundt 26 sekunder (PDL pluss tiltakshistorikk), mens søknadsfronten gir opp etter ti — den kan derfor umulig ligge i request-pathen.
 *
 * Sammenligningen starter som et sidekall i [skyggescope] og ventes aldri på.
 * Scopet har en egen exception handler i komposisjonsroten, slik at en feil i skyggen logges og stopper der i stedet for å felle noe annet.
 * Det er også grunnen til at det ikke står noen `catch` her: en skygge som må ta imot feil for å overleve, er en modell som ikke er total nok, og det er i så fall funnet.
 *
 * Skyggen kalles bare med ferske svar fra gammel vei — se `fraCache` på `TiltakshistorikkSvar`.
 */
class TiltaksdeltakelseSkygge(
    private val tiltakshistorikkHenter: TiltakshistorikkHenter,
    private val metricsCollector: MetricsCollector,
    private val skyggescope: CoroutineScope,
    private val clock: Clock,
    private val sikkerlogg: Sikkerlogg,
    private val påslag: Boolean,
) {
    private val log = KotlinLogging.logger {}

    /**
     * Siste skanse: en feil i skyggen skal aldri kunne komme ut av sidekallet.
     * Den hører her og ikke i komposisjonsroten, fordi det er skyggen som skal dø alene når den svikter.
     *
     * Nivået er `warn` og ikke `error` med vilje: ingen bruker og ingen søknad er berørt når skyggen svikter, og en feil ingen kan handle på skal ikke se ut som en driftsfeil i loggene.
     */
    private val sikkerhetsnett = CoroutineExceptionHandler { _, throwable ->
        log.warn(throwable) { "Skyggekjøringen av tiltaksdeltakelse feilet uventet. Søknaden er ikke berørt." }
    }

    /**
     * Starter sammenligningen og returnerer med det samme.
     * Er skyggen slått av, skjer ingenting — og ny vei kalles ikke, så en app uten tilgang hos Team Valp merker den ikke.
     */
    fun kjørISkyggen(fnr: Fnr, gammel: List<TiltakshistorikkDTO>, correlationId: CorrelationId) {
        if (!påslag) return
        skyggescope.launch(sikkerhetsnett) { sammenlign(fnr = fnr, gammel = gammel, correlationId = correlationId) }
    }

    /**
     * Selve sammenligningen, uten sidekallet rundt, slik at den kan kjøres deterministisk i test.
     */
    suspend fun sammenlign(fnr: Fnr, gammel: List<TiltakshistorikkDTO>, correlationId: CorrelationId) {
        tiltakshistorikkHenter.hentTiltakshistorikk(fnr = fnr, correlationId = correlationId).fold(
            ifLeft = { feil ->
                metricsCollector.tiltaksdeltakelseSkyggeKjøringer.labels(UTFALL_NY_VEI_FEILET).inc()
                feil.logg()
            },
            ifRight = { resultat ->
                resultat.identoppslag.logg()
                rapporter(
                    utfall = sammenlignSkygge(gammel = gammel, ny = resultat.tiltakshistorikk, iDato = LocalDate.now(clock)),
                    respons = resultat.respons,
                )
            },
        )
    }

    /**
     * Hente-tjenesten i libs logger ikke selv — den leverer materialet, og alvorligheten er vår å bestemme.
     * Her er den `warn`: ingen bruker og ingen søknad er berørt når skyggen ikke får svar, og en feil ingen kan handle på skal ikke se ut som en driftsfeil.
     * Den ekte veien vil logge de samme feilene som `error` den dagen den overtar.
     */
    private fun KunneIkkeHenteTiltakshistorikk.logg() {
        when (this) {
            is KunneIkkeHenteTiltakshistorikk.IdentoppslagFeilet ->
                httpKlientError.loggSkyggefeil("identoppslaget mot PDL")

            is KunneIkkeHenteTiltakshistorikk.KallFeilet ->
                httpKlientError.loggSkyggefeil("oppslaget mot tiltakshistorikk")

            is KunneIkkeHenteTiltakshistorikk.UgyldigRespons -> {
                // Beskrivelsen er vår egen ordlyd uten verdier fra svaret, og er trygg i vanlig logg; den rå responsen er det bare sikkerlogg som får se.
                log.warn { "Skyggekjøring av tiltaksdeltakelse: svaret kunne ikke tolkes. $beskrivelse. ${sikkerlogg.seSikkerlogg}" }
                sikkerlogg.warn { "Skyggekjøring av tiltaksdeltakelse: svaret kunne ikke tolkes. $beskrivelse. Response: ${metadata.rawResponseString}" }
            }
        }
    }

    private fun HttpKlientError.loggSkyggefeil(operasjon: String) {
        log.warn { "Skyggekjøring av tiltaksdeltakelse: $operasjon feilet. Status: ${metadata.statusCode}, forsøk: ${metadata.attempts}. ${sikkerlogg.seSikkerlogg}" }
        sikkerlogg.warn { "Skyggekjøring av tiltaksdeltakelse: $operasjon feilet. Request: ${metadata.rawRequestString}. Response: ${metadata.rawResponseString}." }
    }

    /**
     * Fallbacken var tidligere en `error` inne i libs, og dermed usynlig for oss.
     * Den er verdt å vite om selv når oppslaget lykkes: vi kan ha fått historikken for færre identer enn personen faktisk har hatt, og da mangler deltakelser uten at noe feilet.
     * Identene er fødselsnumre og telles bare i vanlig logg; selve verdiene hører i sikkerlogg.
     */
    private fun Identoppslag.logg() {
        if (this !is Identoppslag.FaltTilbakeTilInnsendtFnr) return
        metricsCollector.tiltaksdeltakelseSkyggeIdentfallback.labels(grunn::class.simpleName ?: "ukjent").inc()
        log.warn { "Skyggekjøring av tiltaksdeltakelse: PDL ga ingen brukbare identer, falt tilbake til innsendt fnr (${grunn::class.simpleName}). ${sikkerlogg.seSikkerlogg}" }
        sikkerlogg.warn { "Skyggekjøring av tiltaksdeltakelse: PDL ga ingen brukbare identer, falt tilbake til innsendt fnr. Grunn: $grunn. Response: ${grunn.metadata.rawResponseString}" }
    }

    /**
     * Tellerne står alltid; logglinjen kommer bare når de to veiene er uenige.
     * Den vanlige linja bærer antall og klassifisering, som er PII-fritt, mens id-er og verdier hører i sikkerlogg — arrangørnavn og deltakelses-id-er er personhenførbare i kontekst.
     */
    private fun rapporter(utfall: Skyggeutfall, respons: HttpKlientResponse<Tiltakshistorikk>) {
        utfall.feltavvik.forEach { metricsCollector.tiltaksdeltakelseSkyggeFeltavvik.labels(it.felt).inc() }
        utfall.kunIGammel.forEach { metricsCollector.tiltaksdeltakelseSkyggeKunIGammel.labels(it.grunn).inc() }
        utfall.kunINy.forEach { metricsCollector.tiltaksdeltakelseSkyggeKunINy.labels(it.kildestatus).inc() }
        utfall.ukjenteKildeverdier.forEach { metricsCollector.tiltaksdeltakelseSkyggeUkjentKildeverdi.labels(it.hva).inc() }
        utfall.manglendeKilder.forEach { metricsCollector.tiltaksdeltakelseSkyggeManglendeKilde.labels(it.name).inc() }

        // Hvor mange rader vi faktisk har sammenlignet er svaret på «hvor mye har vi egentlig verifisert» — og dermed på når vi tør bytte vei.
        metricsCollector.tiltaksdeltakelseSkyggeSammenlignedeDeltakelser.inc(utfall.antallFelles.toDouble())
        metricsCollector.tiltaksdeltakelseSkyggeKjøringer.labels(utfall.utfallsmerkelapp()).inc()

        if (!utfall.harAvvik) return

        log.warn {
            "Skyggekjøring av tiltaksdeltakelse fant avvik: ${utfall.antallFelles} deltakelser i begge veier, " +
                "feltavvik ${utfall.feltavvik.groupingBy { it.felt }.eachCount()}, " +
                "kun i gammel vei ${utfall.kunIGammel.groupingBy { it.grunn }.eachCount()}, " +
                "kun i ny vei ${utfall.kunINy.groupingBy { it.kildestatus }.eachCount()}. ${sikkerlogg.seSikkerlogg}"
        }
        // Rå respons følger kun med når det faktisk er noe å forklare — det er da man trenger å se hva tiltakshistorikk svarte for å skjønne hvorfor en rad havnet i feil bøtte.
        sikkerlogg.warn {
            "Skyggekjøring av tiltaksdeltakelse fant avvik. Feltavvik: ${utfall.feltavvik}. Kun i gammel vei: ${utfall.kunIGammel}. Kun i ny vei: ${utfall.kunINy}. Response: ${respons.rawResponseString}"
        }
    }
}

/**
 * Skiller de tre utfallene fra hverandre.
 *
 * [UTFALL_TOMT] finnes fordi et oppslag uten deltakelser ellers ville telt som enighet: to tomme lister er trivielt like, og en skygge som kan melde «alt stemmer» uten å ha sammenlignet noe er ikke et avslutningskriterium å stole på.
 */
private fun Skyggeutfall.utfallsmerkelapp(): String = when {
    harAvvik -> UTFALL_AVVIK
    antallFelles > 0 -> UTFALL_LIKT
    else -> UTFALL_TOMT
}

/** De to veiene ga samme utvalg, med samme verdier på feltene søknaden bærer — og utvalget var ikke tomt. */
const val UTFALL_LIKT = "likt"

/** Ingen av veiene ga noe å sammenligne, så kjøringen sier ingenting om hvorvidt de er enige. */
const val UTFALL_TOMT = "tomt"

/** De to veiene var uenige om minst én rad eller ett felt. */
const val UTFALL_AVVIK = "avvik"

/** Ny vei svarte ikke, så det finnes ikke noe å sammenligne — skilles fra «likt», som ellers ville blitt kunstig høyt. */
const val UTFALL_NY_VEI_FEILET = "ny_vei_feilet"
