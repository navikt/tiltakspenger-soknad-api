package no.nav.tiltakspenger.soknad.api.pdl

import io.ktor.server.config.ApplicationConfig

class PdlService(
    applicationConfig: ApplicationConfig,
) {
    private val pdlClientTokenX = PdlClientTokenX(config = applicationConfig)
    private val pdlClientCredentials = PdlCredentialsClient(config = applicationConfig)

    suspend fun hentPersonaliaMedBarn(fødselsnummer: String, subjectToken: String): PersonDTO {
        if (fødselsnummer == null) {
            throw IllegalStateException("Fødselsnummer er null")
        }

        val result = pdlClientTokenX.fetchSøker(fødselsnummer = fødselsnummer, subjectToken = subjectToken)
        if (result.isSuccess) {
            val person = result.getOrNull()!!.toPerson()
            val barnsIdenter = person.barnsIdenter()
            val barn = barnsIdenter.map { barnsIdent ->
                pdlClientCredentials.fetchBarn(barnsIdent).getOrNull()?.toPerson()
            }.mapNotNull { it }
            return person.toPersonDTO(barn)
        }

        throw IllegalStateException("Noe gikk galt under kall til PDL")
    }
}
