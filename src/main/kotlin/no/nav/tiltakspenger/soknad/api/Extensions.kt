package no.nav.tiltakspenger.soknad.api

import java.time.LocalDate

fun LocalDate.isSameOrAfter(otherDate: LocalDate): Boolean {
    return this.isEqual(otherDate) || this.isAfter(otherDate)
}

fun LocalDate.isSameOrBefore(otherDate: LocalDate): Boolean {
    return this.isEqual(otherDate) || this.isBefore(otherDate)
}
