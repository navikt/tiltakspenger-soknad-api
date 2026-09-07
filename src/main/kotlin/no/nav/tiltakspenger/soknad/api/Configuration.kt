package no.nav.tiltakspenger.soknad.api

const val KAFKA_CONSUMER_GROUP_ID = "tiltakspenger-soknad-api-consumer"

/**
 * Cluster-navnet er det eneste som skiller miljøene, og det tas inn som parameter.
 * Dermed kan DEV- og PROD-grenene testes uten å mutere JVM-global systemtilstand som deles med andre tester.
 */
fun configForMiljø(clusterName: String?): EnvironmentConfig {
    return when (clusterName) {
        "prod-gcp" -> ProdConfig
        "dev-gcp" -> DevConfig
        else -> LocalConfig
    }
}

private fun hentConfigForMiljø(): EnvironmentConfig = configForMiljø(System.getenv("NAIS_CLUSTER_NAME"))

object Configuration : EnvironmentConfig by hentConfigForMiljø() {
    fun isNais(): Boolean = environmentProfile != EnvironmentProfile.LOCAL

    fun isProd(): Boolean = environmentProfile == EnvironmentProfile.PROD

    fun isLocalOrDev(): Boolean = !isProd()

    /**
     * PDL-urlen peker rett på GraphQL-endepunktet, mens `PdlIdentklient` fra libs legger på `/graphql` selv.
     * Suffikset strippes her framfor å holde en egen verdi, slik at de to PDL-klientene våre fortsatt har én kilde til sannhet for hvor PDL står.
     */
    val pdlBaseUrl: String by lazy { pdlUrl.removeSuffix("/graphql") }

    // Settes automatisk av nais; brukes til å bygge den klikkbare sikkerlogg-lenken i KotlinLoggingSikkerlogg.
    // Nullable fordi de ikke finnes lokalt — da faller lenken tilbake til ren tekst.
    val naisAppName: String? by lazy { System.getenv("NAIS_APP_NAME") }
    val gcpTeamProjectId: String? by lazy { System.getenv("GCP_TEAM_PROJECT_ID") }

    data class DataBaseConf(val url: String)

    fun database(): DataBaseConf = DataBaseConf(url = dbJdbcUrl)
}
