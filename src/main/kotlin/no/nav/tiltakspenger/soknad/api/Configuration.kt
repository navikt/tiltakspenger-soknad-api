package no.nav.tiltakspenger.soknad.api

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

private const val APPLICATION_NAME = "tiltakspenger-soknad-api"
const val KAFKA_CONSUMER_GROUP_ID = "$APPLICATION_NAME-consumer"

enum class Profile {
    LOCAL,
    DEV,
    PROD,
}

object Configuration {
    private val defaultProperties =
        ConfigurationMap(
            mapOf(
                "application.httpPort" to 8080.toString(),
                "DB_JDBC_URL" to System.getenv("DB_JDBC_URL"),
                "ELECTOR_PATH" to System.getenv("ELECTOR_PATH"),
                "logback.configurationFile" to "logback.xml",
                "PDL_SCOPE" to System.getenv("PDL_SCOPE"),
                "PDL_ENDPOINT_URL" to System.getenv("PDL_ENDPOINT_URL"),
                "DOKARKIV_SCOPE" to System.getenv("DOKARKIV_SCOPE"),
                "DOKARKIV_ENDPOINT_URL" to System.getenv("DOKARKIV_ENDPOINT_URL"),
                "VEDTAK_SCOPE" to System.getenv("VEDTAK_SCOPE"),
                "TILTAKSPENGER_VEDTAK_ENDPOINT_URL" to System.getenv("TILTAKSPENGER_VEDTAK_ENDPOINT_URL"),
                "NORG2_SCOPE" to System.getenv("NORG2_SCOPE"),
                "NORG2_ENDPOINT_URL" to System.getenv("NORG2_ENDPOINT_URL"),
                "PDF_ENDPOINT_URL" to System.getenv("PDF_ENDPOINT_URL"),
                "AV_ENDPOINT_URL" to System.getenv("AV_ENDPOINT_URL"),
                "TILTAKSPENGER_TILTAK_SCOPE" to System.getenv("TILTAKSPENGER_TILTAK_SCOPE"),
                "TILTAKSPENGER_TILTAK_ENDPOINT_URL" to System.getenv("TILTAKSPENGER_TILTAK_ENDPOINT_URL"),
                "NAIS_TOKEN_INTROSPECTION_ENDPOINT" to System.getenv("NAIS_TOKEN_INTROSPECTION_ENDPOINT"),
                "NAIS_TOKEN_ENDPOINT" to System.getenv("NAIS_TOKEN_ENDPOINT"),
                "NAIS_TOKEN_EXCHANGE_ENDPOINT" to System.getenv("NAIS_TOKEN_EXCHANGE_ENDPOINT"),
                "IDENTHENDELSE_TOPIC" to "tpts.identhendelse-v1",
            ),
        )
    private val localProperties =
        ConfigurationMap(
            mapOf(
                "application.profile" to Profile.LOCAL.toString(),
                "logback.configurationFile" to "logback.local.xml",
                "DB_JDBC_URL" to "jdbc:postgresql://host.docker.internal:5436/soknad?user=postgres&password=test",
                "PDL_SCOPE" to "localhost",
                "PDL_ENDPOINT_URL" to "http://localhost:8484/personalia",
                "DOKARKIV_SCOPE" to "localhost",
                "DOKARKIV_ENDPOINT_URL" to "http://localhost:8484",
                "VEDTAK_SCOPE" to "localhost",
                "TILTAKSPENGER_VEDTAK_ENDPOINT_URL" to "http://host.docker.internal:8080",
                "NORG2_SCOPE" to "localhost",
                "NORG2_ENDPOINT_URL" to "http://localhost:8484",
                "PDF_ENDPOINT_URL" to "http://localhost:8085",
                "AV_ENDPOINT_URL" to "http://localhost:8484/av",
                "TILTAKSPENGER_TILTAK_SCOPE" to "localhost",
                "TILTAKSPENGER_TILTAK_ENDPOINT_URL" to "http://localhost:8484",
                "NAIS_TOKEN_INTROSPECTION_ENDPOINT" to "http://localhost:7164/api/v1/introspect",
                "NAIS_TOKEN_ENDPOINT" to "http://localhost:7164/api/v1/token",
                "NAIS_TOKEN_EXCHANGE_ENDPOINT" to "http://localhost:7164/api/v1/token",
            ),
        )
    private val devProperties =
        ConfigurationMap(
            mapOf(
                "application.profile" to Profile.DEV.toString(),
            ),
        )
    private val prodProperties =
        ConfigurationMap(
            mapOf(
                "application.profile" to Profile.PROD.toString(),
            ),
        )

    private fun config(): Configuration {
        return when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
            "dev-gcp" ->
                ConfigurationProperties.systemProperties() overriding EnvironmentVariables overriding devProperties overriding defaultProperties

            "prod-gcp" ->
                ConfigurationProperties.systemProperties() overriding EnvironmentVariables overriding prodProperties overriding defaultProperties

            else -> {
                ConfigurationProperties.systemProperties() overriding EnvironmentVariables overriding localProperties overriding defaultProperties
            }
        }
    }

    fun applicationProfile() =
        when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
            "dev-gcp" -> Profile.DEV
            "prod-gcp" -> Profile.PROD
            else -> Profile.LOCAL
        }

    fun logbackConfigurationFile() = config()[Key("logback.configurationFile", stringType)]

    fun httpPort() = config()[Key("application.httpPort", intType)]

    fun isNais() = applicationProfile() != Profile.LOCAL
    fun isProd() = applicationProfile() == Profile.PROD

    fun electorPath(): String = config()[Key("ELECTOR_PATH", stringType)]

    val pdlScope: String by lazy { config()[Key("PDL_SCOPE", stringType)] }
    val dokarkivScope: String by lazy { config()[Key("DOKARKIV_SCOPE", stringType)] }
    val saksbehandlingApiScope: String by lazy { config()[Key("VEDTAK_SCOPE", stringType)] }
    val norg2Scope: String by lazy { config()[Key("NORG2_SCOPE", stringType)] }
    val tiltakspengerTiltakScope: String by lazy { config()[Key("TILTAKSPENGER_TILTAK_SCOPE", stringType)] }

    val pdlUrl by lazy { config()[Key("PDL_ENDPOINT_URL", stringType)] }
    val dokarkivUrl: String by lazy { config()[Key("DOKARKIV_ENDPOINT_URL", stringType)] }
    val saksbehandlingApiUrl: String by lazy { config()[Key("TILTAKSPENGER_VEDTAK_ENDPOINT_URL", stringType)] }
    val norg2Url: String by lazy { config()[Key("NORG2_ENDPOINT_URL", stringType)] }
    val pdfUrl: String by lazy { config()[Key("PDF_ENDPOINT_URL", stringType)] }
    val avUrl: String by lazy { config()[Key("AV_ENDPOINT_URL", stringType)] }
    val tiltakspengerTiltakUrl: String by lazy { config()[Key("TILTAKSPENGER_TILTAK_ENDPOINT_URL", stringType)] }

    val naisTokenIntrospectionEndpoint: String by lazy { config()[Key("NAIS_TOKEN_INTROSPECTION_ENDPOINT", stringType)] }
    val naisTokenEndpoint: String by lazy { config()[Key("NAIS_TOKEN_ENDPOINT", stringType)] }
    val tokenExchangeEndpoint: String by lazy { config()[Key("NAIS_TOKEN_EXCHANGE_ENDPOINT", stringType)] }

    val identhendelseTopic: String by lazy { config()[Key("IDENTHENDELSE_TOPIC", stringType)] }

    data class DataBaseConf(
        val url: String,
    )
    fun database() = DataBaseConf(
        url = config()[Key("DB_JDBC_URL", stringType)],
    )
}
