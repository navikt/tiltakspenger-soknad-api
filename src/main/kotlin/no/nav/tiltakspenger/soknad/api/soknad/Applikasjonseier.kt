package no.nav.tiltakspenger.soknad.api.soknad

/** Avgjør hvilken applikasjon som eier søknaden; Arena eller den nye tiltakspengerløsningen */
enum class Applikasjonseier {
    Arena,
    Tiltakspenger,
    ;

    fun toDb(): String {
        return when (this) {
            Arena -> "arena"
            Tiltakspenger -> "tp"
        }
    }

    companion object {
        fun toApplikasjonseier(eier: String): Applikasjonseier {
            return when (eier) {
                "arena" -> Arena
                "tp" -> Tiltakspenger
                else -> throw IllegalStateException("Ukjent eier i databasen: $eier. Forventet: ['arena','tp']")
            }
        }
    }
}
