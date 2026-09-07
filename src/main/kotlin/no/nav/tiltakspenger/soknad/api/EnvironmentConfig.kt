package no.nav.tiltakspenger.soknad.api

enum class EnvironmentProfile {
    LOCAL,
    DEV,
    PROD,
}

sealed interface EnvironmentConfig {
    val environmentProfile: EnvironmentProfile
    val httpPort: Int
    val logbackConfigurationFile: String

    val electorPath: String
    val dbJdbcUrl: String

    val tokenEndpoint: String
    val tokenIntrospectionEndpoint: String
    val tokenExchangeEndpoint: String

    val pdlScope: String
    val pdlUrl: String

    val dokarkivScope: String
    val dokarkivUrl: String

    val saksbehandlingApiScope: String
    val saksbehandlingApiUrl: String

    val pdfgenrsUrl: String
    val avUrl: String

    val tiltakshistorikkScope: String
    val tiltakshistorikkUrl: String

    val identhendelseTopic: String
}

data object LocalConfig : EnvironmentConfig {
    override val environmentProfile = EnvironmentProfile.LOCAL
    override val httpPort = 8080
    override val logbackConfigurationFile = "logback.local.xml"

    // Leader election kjører kun i Nais, men verdien må finnes for at oppslaget ikke skal feile lokalt.
    override val electorPath = "http://localhost:4040"
    override val dbJdbcUrl = "jdbc:postgresql://host.docker.internal:5436/soknad?user=postgres&password=test"

    override val tokenEndpoint = "http://localhost:7164/api/v1/token"
    override val tokenIntrospectionEndpoint = "http://localhost:7164/api/v1/introspect"
    override val tokenExchangeEndpoint = "http://localhost:7164/api/v1/token"

    override val pdlScope = "localhost"
    override val pdlUrl = "http://localhost:8484/personalia"

    override val dokarkivScope = "localhost"
    override val dokarkivUrl = "http://localhost:8484"

    override val saksbehandlingApiScope = "localhost"
    override val saksbehandlingApiUrl = "http://host.docker.internal:8080"

    override val pdfgenrsUrl = "http://localhost:8084"
    override val avUrl = "http://localhost:8484/av"

    override val tiltakshistorikkScope = "localhost"
    override val tiltakshistorikkUrl = "http://localhost:8484"

    override val identhendelseTopic = "tpts.identhendelse-v1"
}

data object DevConfig : EnvironmentConfig {
    override val environmentProfile = EnvironmentProfile.DEV
    override val httpPort = 8080
    override val logbackConfigurationFile = "logback.xml"

    override val electorPath: String = System.getenv("ELECTOR_PATH")
    override val dbJdbcUrl: String = System.getenv("DB_JDBC_URL")

    override val tokenEndpoint: String = System.getenv("NAIS_TOKEN_ENDPOINT")
    override val tokenIntrospectionEndpoint: String = System.getenv("NAIS_TOKEN_INTROSPECTION_ENDPOINT")
    override val tokenExchangeEndpoint: String = System.getenv("NAIS_TOKEN_EXCHANGE_ENDPOINT")

    override val pdlScope = "dev-fss:pdl:pdl-api"
    override val pdlUrl = "https://pdl-api.dev-fss-pub.nais.io/graphql"

    override val dokarkivScope = "dev-fss:teamdokumenthandtering:dokarkiv"
    override val dokarkivUrl = "https://dokarkiv-q2.dev-fss-pub.nais.io"

    override val saksbehandlingApiScope = "dev-gcp:tpts:tiltakspenger-saksbehandling-api"
    override val saksbehandlingApiUrl = "http://tiltakspenger-saksbehandling-api"

    override val pdfgenrsUrl = "http://tiltakspenger-pdfgenrs"
    override val avUrl = "http://clamav.nais-system/scan"

    override val tiltakshistorikkScope = "dev-gcp:team-mulighetsrommet:tiltakshistorikk"
    override val tiltakshistorikkUrl = "http://tiltakshistorikk.team-mulighetsrommet"

    override val identhendelseTopic = "tpts.identhendelse-v1"
}

data object ProdConfig : EnvironmentConfig {
    override val environmentProfile = EnvironmentProfile.PROD
    override val httpPort = 8080
    override val logbackConfigurationFile = "logback.xml"

    override val electorPath: String = System.getenv("ELECTOR_PATH")
    override val dbJdbcUrl: String = System.getenv("DB_JDBC_URL")

    override val tokenEndpoint: String = System.getenv("NAIS_TOKEN_ENDPOINT")
    override val tokenIntrospectionEndpoint: String = System.getenv("NAIS_TOKEN_INTROSPECTION_ENDPOINT")
    override val tokenExchangeEndpoint: String = System.getenv("NAIS_TOKEN_EXCHANGE_ENDPOINT")

    override val pdlScope = "prod-fss:pdl:pdl-api"
    override val pdlUrl = "https://pdl-api.prod-fss-pub.nais.io/graphql"

    override val dokarkivScope = "prod-fss:teamdokumenthandtering:dokarkiv"
    override val dokarkivUrl = "https://dokarkiv.prod-fss-pub.nais.io"

    override val saksbehandlingApiScope = "prod-gcp:tpts:tiltakspenger-saksbehandling-api"
    override val saksbehandlingApiUrl = "http://tiltakspenger-saksbehandling-api"

    override val pdfgenrsUrl = "http://tiltakspenger-pdfgenrs"
    override val avUrl = "http://clamav.nais-system/scan"

    override val tiltakshistorikkScope = "prod-gcp:team-mulighetsrommet:tiltakshistorikk"
    override val tiltakshistorikkUrl = "http://tiltakshistorikk.team-mulighetsrommet"

    override val identhendelseTopic = "tpts.identhendelse-v1"
}
