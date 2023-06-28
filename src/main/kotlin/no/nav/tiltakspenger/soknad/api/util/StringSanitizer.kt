package no.nav.tiltakspenger.soknad.api.util

import org.apache.commons.text.StringEscapeUtils

object StringSanitizer {
    fun sanitize(str: String): String {
        var escapedString = StringEscapeUtils.escapeHtml4(str)
        escapedString = escapedString.replace("&aelig;", "æ")
        escapedString = escapedString.replace("&oslash;", "ø")
        escapedString = escapedString.replace("&aring;", "å")
        return escapedString
    }
}
