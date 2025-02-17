package no.nav.tiltakspenger.soknad.api

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

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
            ),
        )
    private val localProperties =
        ConfigurationMap(
            mapOf(
                "application.profile" to Profile.LOCAL.toString(),
                "logback.configurationFile" to "logback.local.xml",
                "DB_JDBC_URL" to "jdbc:postgresql://host.docker.internal:5436/soknad?user=postgres&password=test",
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

    fun isNais() = applicationProfile() != Profile.LOCAL
    fun isProd() = applicationProfile() == Profile.PROD

    fun electorPath(): String = config()[Key("ELECTOR_PATH", stringType)]

    data class DataBaseConf(
        val url: String,
    )
    fun database() = DataBaseConf(
        url = config()[Key("DB_JDBC_URL", stringType)],
    )
}
