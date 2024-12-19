import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val javaVersion = JavaVersion.VERSION_21
val mockkVersion = "1.13.13"
val ktorVersion = "2.3.12"
val kotestVersion = "5.9.1"
val jacksonVersion = "2.18.2"
val kotlinxCoroutinesVersion = "1.9.0"
val tokenSupportVersion = "5.0.13"
val prometheusVersion = "0.16.0"
val apacheCommonsTextVersion = "1.13.0"
val unleashVersion = "9.2.6"
val pdfboxVersion = "3.0.3"
val felleslibVersion = "0.0.309"
val flywayVersjon = "11.1.0"
val testContainersVersion = "1.20.4"

plugins {
    application
    distribution
    kotlin("jvm") version "2.1.0"
    // id("ca.cutterslade.analyze") version "1.9.1"
    id("com.diffplug.spotless") version "6.25.0"
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation(kotlin("stdlib"))
    implementation("ch.qos.logback:logback-classic:1.5.14")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("org.jetbrains:annotations:26.0.1")
    implementation("com.natpryce:konfig:1.6.10.0")
    implementation("com.github.navikt.tiltakspenger-libs:soknad-dtos:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:tiltak-dtos:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:common:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:jobber:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:personklient-domene:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:personklient-infrastruktur:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:json:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:logging:$felleslibVersion")
    implementation("org.apache.commons:commons-text:$apacheCommonsTextVersion")
    implementation("io.getunleash:unleash-client-java:$unleashVersion")

    // Ktor
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-http:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-utils:$ktorVersion")
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-request-validation:$ktorVersion")

    // Arrowe
    implementation("io.arrow-kt:arrow-core:2.0.0")

    // Prometheus
    implementation("io.prometheus:simpleclient:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // DB
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersjon")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.github.seratch:kotliquery:1.9.0")

    // TokenX
    implementation("no.nav.security:token-validation-ktor-v2:$tokenSupportVersion")
    implementation("no.nav.security:token-client-core:$tokenSupportVersion")

    // PDF handling
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")

    // Apache Tika
    implementation("org.apache.tika:tika-core:3.0.0")

    // Caffeine
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.0")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.mockk:mockk-dsl-jvm:$mockkVersion")
    testImplementation("org.skyscreamer:jsonassert:1.5.3")
    testImplementation("no.nav.security:mock-oauth2-server:2.1.10")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.mockk:mockk-dsl-jvm:$mockkVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
    testImplementation("io.kotest:kotest-extensions:$kotestVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:$kotlinxCoroutinesVersion")
    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testContainersVersion")
    testImplementation("org.testcontainers:postgresql:$testContainersVersion")
}

application {
    mainClass.set("no.nav.tiltakspenger.soknad.api.ApplicationKt")
}

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

apply(plugin = "com.diffplug.spotless")

spotless {
    kotlin {
        ktlint("0.48.2")
    }
}

tasks {
    kotlin {
        compileKotlin {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }
        }
        compileTestKotlin {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }
        }
    }

    jar {
        dependsOn(configurations.runtimeClasspath)

        manifest {
            attributes["Main-Class"] = "no.nav.tiltakspenger.soknad.api.ApplicationKt"
            attributes["Class-Path"] = configurations.runtimeClasspath
                .get()
                .joinToString(separator = " ") { file -> file.name }
        }
    }

    test {
        // JUnit 5 support
        useJUnitPlatform()
        // https://phauer.com/2018/best-practices-unit-testing-kotlin/
        systemProperty("junit.jupiter.testinstance.lifecycle.default", "per_class")

        testLogging {
            events = setOf(
                org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
                org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
                org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
            )
        }
    }

    register<Copy>("gitHooks") {
        from(file(".scripts/pre-commit"))
        into(file(".git/hooks"))
    }

    /*
    analyzeClassesDependencies {
        warnUsedUndeclared = true
        warnUnusedDeclared = true
    }
    analyzeTestClassesDependencies {
        warnUsedUndeclared = true
        warnUnusedDeclared = true
    }
     */
}
