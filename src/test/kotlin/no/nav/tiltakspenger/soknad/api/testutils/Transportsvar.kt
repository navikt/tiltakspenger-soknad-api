package no.nav.tiltakspenger.soknad.api.testutils

import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport

/**
 * Køer samme feilstatus for hvert forsøk klienten kommer til å gjøre.
 * Klientene mot tiltak, dokarkiv og saksbehandling-api er satt opp med `Retry.Fast(maksForsøk = 4)`, og hvert forsøk henter sitt eget svar fra køen.
 */
fun FakeHttpTransport.leggIKøStatusForAlleForsøk(
    statusCode: Int,
    body: String = "",
    maksForsøk: Int = 4,
) {
    repeat(maksForsøk) { leggIKøStatus(statusCode, body) }
}
