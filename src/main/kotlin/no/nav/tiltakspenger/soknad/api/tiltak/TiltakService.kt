package no.nav.tiltakspenger.soknad.api.tiltak

import arrow.core.Either
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.httpklient.loggFeil
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakshistorikk
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.Identoppslag
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.KunneIkkeHenteTiltakshistorikk
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.TiltakshistorikkHenter
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.somKildenTilsierManKanSøkePå
import java.time.Clock
import java.time.LocalDate

/**
 * Tiltaksdeltakelsene søknaden lar bruker velge blant.
 *
 * Hentingen gjør to oppslag etter hverandre — identer fra PDL, så historikk fra `tiltakshistorikk` hos Team Valp — og begge går med systemtoken.
 * Brukertokenet autentiserer bare inn til oss; det er `fnr` fra tokenet som styrer hvem vi slår opp.
 *
 * Uttrekket er to innsnevringer på rad, og begge er nødvendige:
 * `somKildenTilsierManKanSøkePå` er den delte søknadsguarden i libs (tiltakstypen må gi rett, statusen må være kjent og gi rett til å søke), mens tidsromsfilteret er søknadens eget vindu på seks måneder tilbake og to fram.
 *
 * Tjenesten er stedet feil logges, én gang per feilsituasjon: klientene og hente-tjenesten i libs logger ikke selv, fordi alvorligheten avhenger av hvem som venter på svaret.
 * Her venter en bruker som ellers ikke får søkt, så alt som feller oppslaget er `error`.
 */
class TiltakService(
    private val tiltakshistorikkHenter: TiltakshistorikkHenter,
    private val clock: Clock,
    private val sikkerlogg: Sikkerlogg,
) {
    private val log = KotlinLogging.logger {}

    suspend fun hentTiltak(
        fnr: Fnr,
        maskerArrangørnavn: Boolean,
        correlationId: CorrelationId,
    ): Either<KunneIkkeHenteTiltakshistorikk, List<TiltaksdeltakelseDto>> =
        tiltakshistorikkHenter.hentTiltakshistorikk(fnr = fnr, correlationId = correlationId)
            .onLeft { it.logg() }
            .map { resultat ->
                resultat.identoppslag.logg()
                resultat.tiltakshistorikk.loggUkjenteKildeverdier()
                resultat.tiltakshistorikk.tilSøkbareDeltakelser(maskerArrangørnavn)
            }

    private fun Tiltakshistorikk.tilSøkbareDeltakelser(maskerArrangørnavn: Boolean): List<TiltaksdeltakelseDto> =
        deltakelser.somKildenTilsierManKanSøkePå(LocalDate.now(clock)).deltakelser
            .toTiltakDto(maskerArrangørnavn)
            .filter { it.erInnenforRelevantTidsrom(clock) }

    /**
     * Alle tre feilene ender som 500 til bruker, og logges derfor på `error`.
     * Feilene fra de to kallene deler `loggFeil`, som allerede skiller PII-fri linje i vanlig logg fra rå request og respons i sikkerlogg.
     */
    private fun KunneIkkeHenteTiltakshistorikk.logg() {
        when (this) {
            is KunneIkkeHenteTiltakshistorikk.IdentoppslagFeilet ->
                httpKlientError.loggFeil(log, "identoppslag mot PDL ved henting av tiltaksdeltakelser", "", sikkerlogg)

            is KunneIkkeHenteTiltakshistorikk.KallFeilet ->
                httpKlientError.loggFeil(log, "henting av tiltaksdeltakelser fra tiltakshistorikk", "", sikkerlogg)

            is KunneIkkeHenteTiltakshistorikk.UgyldigRespons -> {
                // Beskrivelsen er vår egen ordlyd uten verdier fra svaret, og er trygg i vanlig logg; den rå responsen er det bare sikkerlogg som får se.
                log.error { "Henting av tiltaksdeltakelser: svaret fra tiltakshistorikk kunne ikke tolkes. $beskrivelse. ${sikkerlogg.seSikkerlogg}" }
                sikkerlogg.error { "Henting av tiltaksdeltakelser: svaret fra tiltakshistorikk kunne ikke tolkes. $beskrivelse. Response: ${metadata.rawResponseString}" }
            }
        }
    }

    /**
     * Fallbacken er verdt å vite om selv når oppslaget lykkes: vi kan ha fått historikken for færre identer enn personen faktisk har hatt, og da mangler deltakelser uten at noe feilet.
     * Nivået er `warn` og ikke `error` fordi bruker fortsatt får et svar å søke på.
     * Identene er fødselsnumre og hører kun i sikkerlogg.
     */
    private fun Identoppslag.logg() {
        if (this !is Identoppslag.FaltTilbakeTilInnsendtFnr) return
        log.warn { "Henting av tiltaksdeltakelser: PDL ga ingen brukbare identer, falt tilbake til innsendt fnr (${grunn::class.simpleName}). ${sikkerlogg.seSikkerlogg}" }
        sikkerlogg.warn { "Henting av tiltaksdeltakelser: PDL ga ingen brukbare identer, falt tilbake til innsendt fnr. Grunn: $grunn. Response: ${grunn.metadata.rawResponseString}" }
    }

    /**
     * Koder fra kilden vi ikke kjenner igjen — statuser, tiltakskoder, Komet-årsaker og deltakelsesformer.
     * De feller ikke oppslaget, men en deltakelse med ukjent status eller tiltakskode når aldri søknadsguarden, så bruker kan miste noe uten at noe annet slår ut.
     * Både [no.nav.tiltakspenger.libs.tiltaksdeltakelse.UkjentKildeverdi.hva] og koden i kontrakten er kildevokabular uten personopplysninger, og hører derfor i vanlig logg.
     */
    private fun Tiltakshistorikk.loggUkjenteKildeverdier() {
        ukjenteKildeverdier.forEach {
            log.warn { "Henting av tiltaksdeltakelser: ukjent ${it.hva} fra kilden: ${it.kodeIKontrakten}." }
        }
    }
}
