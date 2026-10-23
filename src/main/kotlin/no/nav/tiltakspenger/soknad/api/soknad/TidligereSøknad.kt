package no.nav.tiltakspenger.soknad.api.soknad

import no.nav.tiltakspenger.libs.common.SøknadId
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * En søknad brukeren allerede har sendt inn, med svarene brukeren ga.
 *
 * @property svar Svarene slik de ble sendt inn.
 * Vedleggene er ikke med.
 * @property behandlingsutfall Utfallet fra saksbehandling-api, eller null om søknaden ikke er ferdig behandlet.
 */
data class TidligereSøknad(
    val id: SøknadId,
    val mottatt: LocalDateTime,
    val svar: SpørsmålsbesvarelserDTO,
    val behandlingsutfall: Behandlingsutfall?,
) {
    val aktivitetId: String get() = svar.tiltak.aktivitetId
    val periode: Periode get() = svar.tiltak.periode
    val erFerdigBehandlet: Boolean get() = behandlingsutfall != null

    /** Om [nyeSvar] har de samme svarene som denne søknaden, se [sammenlignbareSvar]. */
    fun harSammeSvarSom(nyeSvar: SpørsmålsbesvarelserDTO): Boolean = svar.sammenlignbareSvar() == nyeSvar.sammenlignbareSvar()
}

/**
 * Den sist mottatte søknaden for samme tiltaksdeltakelse der søkt periode dekker hele [periode].
 * En periode som går utenfor det som allerede er søkt, for eksempel ved forlengelse av deltakelsen, er ikke dekket.
 */
fun List<TidligereSøknad>.søknadSomDekker(aktivitetId: String, periode: Periode): TidligereSøknad? =
    filter { it.aktivitetId == aktivitetId && periode.erInnenfor(it.periode) }
        .maxByOrNull { it.mottatt }

/**
 * Den tidligere søknaden en ny søknad med [nyeSvar] er en duplikat av, eller null om den ikke er en duplikat.
 *
 * Så lenge søknaden er under behandling, kan bruker søke på nytt for samme tiltaksdeltakelse og periode bare med nye eller endrede opplysninger.
 * Når søknaden er ferdig behandlet (innvilget eller avslått), kan bruker sende den samme søknaden på nytt.
 * Vi sammenligner med den sist mottatte søknaden som dekker perioden ([søknadSomDekker]).
 * Vedlegg teller ikke; de kan ettersendes.
 */
fun List<TidligereSøknad>.duplikatAv(nyeSvar: SpørsmålsbesvarelserDTO): TidligereSøknad? =
    søknadSomDekker(aktivitetId = nyeSvar.tiltak.aktivitetId, periode = nyeSvar.tiltak.periode)
        ?.takeIf { !it.erFerdigBehandlet && it.harSammeSvarSom(nyeSvar) }

/**
 * Bare svarene brukeren faktisk har gitt, normalisert slik at to like besvarelser blir like.
 *
 * Utelatt: tiltakets beskrivelse (navn, arrangør, type) som kommer fra kilden, perioden som allerede er sjekket av [søknadSomDekker], og bekreftelsene som alltid er krysset av.
 * En periode eller dato teller bare når svaret den hører til er ja, og svarene under «andre utbetalinger» teller bare når bruker svarte ja på det.
 * Rekkefølgen på barna teller ikke.
 */
private fun SpørsmålsbesvarelserDTO.sammenlignbareSvar(): SammenlignbareSvar {
    val andreUtbetalinger = mottarAndreUtbetalinger
    return SammenlignbareSvar(
        kvalifiseringsprogram = kvalifiseringsprogram.periode.hvis(kvalifiseringsprogram.deltar),
        introduksjonsprogram = introduksjonsprogram.periode.hvis(introduksjonsprogram.deltar),
        institusjonsopphold = institusjonsopphold.periode.hvis(institusjonsopphold.borPåInstitusjon),
        søkerOmBarnetillegg = barnetillegg.søkerOmBarnetillegg,
        manueltRegistrerteBarn = barnetillegg.manueltRegistrerteBarnSøktBarnetilleggFor.groupingBy { it }.eachCount(),
        registrerteBarn = barnetillegg.registrerteBarnSøktBarnetilleggFor.groupingBy { it }.eachCount(),
        etterlønn = etterlønn.mottar,
        sykepenger = sykepenger.periode.hvis(sykepenger.mottar),
        mottarAndreUtbetalinger = andreUtbetalinger,
        gjenlevendepensjon = gjenlevendepensjon.periode.hvis(andreUtbetalinger && gjenlevendepensjon.mottar == true),
        alderspensjon = alderspensjon.fraDato.hvis(andreUtbetalinger && alderspensjon.mottar == true),
        supplerendestønadover67 = supplerendestønadover67.periode.hvis(andreUtbetalinger && supplerendestønadover67.mottar == true),
        supplerendestønadflyktninger = supplerendestønadflyktninger.periode.hvis(andreUtbetalinger && supplerendestønadflyktninger.mottar == true),
        pensjonsordning = pensjonsordning.periode.hvis(andreUtbetalinger && pensjonsordning.mottar == true),
        jobbsjansen = jobbsjansen.periode.hvis(andreUtbetalinger && jobbsjansen.mottar == true),
    )
}

/** Et ja-svar med eventuell periode eller dato er [Svar.Ja], et nei-svar er [Svar.Nei] uansett hva som ligger igjen i perioden. */
private fun <T> T?.hvis(ja: Boolean): Svar<T> = if (ja) Svar.Ja(this) else Svar.Nei

private sealed interface Svar<out T> {
    data class Ja<T>(val verdi: T?) : Svar<T>
    data object Nei : Svar<Nothing>
}

private data class SammenlignbareSvar(
    val kvalifiseringsprogram: Svar<Periode>,
    val introduksjonsprogram: Svar<Periode>,
    val institusjonsopphold: Svar<Periode>,
    val søkerOmBarnetillegg: Boolean?,
    val manueltRegistrerteBarn: Map<ManueltRegistrertBarn, Int>,
    val registrerteBarn: Map<RegistrertBarn, Int>,
    val etterlønn: Boolean,
    val sykepenger: Svar<Periode>,
    val mottarAndreUtbetalinger: Boolean,
    val gjenlevendepensjon: Svar<Periode>,
    val alderspensjon: Svar<LocalDate>,
    val supplerendestønadover67: Svar<Periode>,
    val supplerendestønadflyktninger: Svar<Periode>,
    val pensjonsordning: Svar<Periode>,
    val jobbsjansen: Svar<Periode>,
)
