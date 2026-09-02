package no.nav.tiltakspenger.soknad.api.testutils

import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.FnrGenerator

/**
 * Delt, trådsikker generator for fødselsnumre i tester, se [FnrGenerator].
 * Brukes der en test trenger et vilkårlig, gyldig (syntetisk) fnr, i stedet for å hardkode et 11-sifret tall
 * — se konsist-regelen `IngenHardkodedeFnr`.
 */
private val fnrGenerator = FnrGenerator()

fun nyttTestFnr(): Fnr = fnrGenerator.generer()

fun nyttTestFødselsnummer(): String = nyttTestFnr().verdi
