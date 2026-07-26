package no.nav.tiltakspenger.soknad.api.testutils

import arrow.atomic.Atomic
import arrow.atomic.update
import arrow.core.Either
import arrow.core.Nel
import arrow.core.right
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.soknad.api.antivirus.AvKlient
import no.nav.tiltakspenger.soknad.api.antivirus.AvSjekkResultat
import no.nav.tiltakspenger.soknad.api.antivirus.Status
import no.nav.tiltakspenger.soknad.api.vedlegg.Vedlegg

/**
 * Fake for [AvKlient], som melder alle vedlegg rene.
 * Sett [standardStatus] for å øve på det motsatte.
 */
class AvKlientFake(
    private val standardStatus: Status = Status.OK,
) : AvKlient {
    private val skannede = Atomic(emptyList<String>())
    val skannedeFilnavn: List<String> get() = skannede.get()

    override suspend fun scan(vedleggsListe: Nel<Vedlegg>): Either<HttpKlientError, List<AvSjekkResultat>> {
        skannede.update { skannet -> skannet + vedleggsListe.map { it.filnavn } }
        return vedleggsListe.map { AvSjekkResultat(filnavn = it.filnavn, resultat = standardStatus) }.right()
    }
}
