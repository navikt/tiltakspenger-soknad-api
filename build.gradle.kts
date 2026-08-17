import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

val mockkVersion = "1.14.11"
val ktorVersion = "3.4.3"
val kotestVersion = "6.2.3"
val lz4Version = "1.11.2"
val jacksonVersion = "3.2.1"
val jacksonAnnotationsVersion = "2.22"
val kotlinxCoroutinesVersion = "1.11.0"
val prometheusVersion = "0.16.0"
val apacheCommonsTextVersion = "1.15.0"
val pdfboxVersion = "3.0.8"
val felleslibVersion = "0.0.20260805225536"
val flywayVersjon = "12.10.0"
val testContainersVersion = "2.0.5"

plugins {
    application
    distribution
    kotlin("jvm") version "2.4.10"
    id("com.diffplug.spotless") version "8.8.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

dependencies {
    // Lås versjonene på alle Kotlin-komponenter til samme versjon
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation(kotlin("stdlib"))

    // Lås alle io.netty:* til samme versjon som forsikring mot fremtidig 4.1/4.2-drift.
    // ktor-server-netty drar inn netty 4.2.x; en BOM hindrer at en transitiv avhengighet
    // senere blander inn 4.1.x og legger duplikate baseklasser på classpath (jf. `-cp lib/*`).
    implementation(platform("io.netty:netty-bom:4.2.17.Final"))

    constraints {
        // kafka-clients (via libs:kafka) drar inn lz4-java 1.10.2, der de native XXHash-
        // implementasjonene kan krasje JVM-en på ugyldige byte-intervaller (GHSA-xx22-p4ch-683r).
        // Transitiv-only, derfor constraint og ikke en deklarert avhengighet.
        implementation("at.yawk.lz4:lz4-java:$lz4Version")
    }

    implementation("ch.qos.logback:logback-classic:1.5.38")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("io.github.oshai:kotlin-logging-jvm:8.0.4")
    implementation("org.jetbrains:annotations:26.1.0")
    implementation("com.natpryce:konfig:1.6.10.0")
    implementation("com.github.navikt.tiltakspenger-libs:soknad-dtos:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:tiltak-dtos:$felleslibVersion")
    // TODO: Modulene er ikke tatt i bruk ennå — direktekall mot tiltakshistorikk er blokkert til tilgangs-PR-en i navikt/mulighetsrommet er merget.
    implementation("com.github.navikt.tiltakspenger-libs:tiltaksdeltakelse-domene:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:tiltaksdeltakelse-infrastruktur:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:common:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:jobber:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:ktor-common:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:httpklient-infrastruktur:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:personklient-domene:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:personklient-infrastruktur:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:json:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:logging:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:kafka:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:periodisering:$felleslibVersion")
    implementation("com.github.navikt.tiltakspenger-libs:texas:$felleslibVersion")
    implementation("org.apache.commons:commons-text:$apacheCommonsTextVersion")

    // Ktor. Kun server-siden: utgående HTTP går via libs httpklient.
    implementation("io.ktor:ktor-http:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson3:$ktorVersion")
    implementation("io.ktor:ktor-utils:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-request-validation:$ktorVersion")

    // Arrow
    implementation("io.arrow-kt:arrow-core:2.2.3")

    // Prometheus
    implementation("io.prometheus:simpleclient:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")

    // Jackson
    implementation("tools.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonAnnotationsVersion")

    // DB
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersjon")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("com.github.seratch:kotliquery:1.9.1")

    // PDF-håndtering
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")

    // Apache Tika
    implementation("org.apache.tika:tika-core:3.3.2")

    // Caffeine
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    // Ktor-klienten brukes kun av testApplication sin innebygde klient i rute-testene; produksjonskoden går via libs httpklient.
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.10")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Delte arkitekturregler; drar inn konsist transitivt (api-avhengighet).
    testImplementation("com.github.navikt.tiltakspenger-libs:konsist-regler:$felleslibVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.mockk:mockk-dsl-jvm:$mockkVersion")
    // Brukes til å lage test-token (PlainJWT). Tidligere transitivt via mock-oauth2-server.
    testImplementation("com.nimbusds:nimbus-jose-jwt:10.9.1")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
    testImplementation("io.kotest:kotest-extensions:$kotestVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:$kotlinxCoroutinesVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-io-core-jvm:0.9.1")
    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:$testContainersVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testContainersVersion")
    // Starter lokal postgres for LokalMain; hører hjemme i test-sourcesettet sammen med den.
    testImplementation("com.github.navikt.tiltakspenger-libs:lokal-oppstart:$felleslibVersion")
    testImplementation("com.github.navikt.tiltakspenger-libs:test-common:$felleslibVersion")
    testImplementation("com.github.navikt.tiltakspenger-libs:persistering-test-common:$felleslibVersion")
    // FakeHttpTransport: ekte HttpKlient med byttet transport, så hele pipelinen kjører i test.
    testImplementation(testFixtures("com.github.navikt.tiltakspenger-libs:httpklient-infrastruktur:$felleslibVersion"))
    testImplementation(testFixtures("com.github.navikt.tiltakspenger-libs:tiltaksdeltakelse-domene:$felleslibVersion"))
}

application {
    mainClass.set("no.nav.tiltakspenger.soknad.api.ApplicationKt")
}

configurations.all {
    // ekskluder JUnit 4
    exclude(group = "junit", module = "junit")
}

apply(plugin = "com.diffplug.spotless")

// --- Kover --------------------------------------------------------------------
// Holder 100 % linjedekning for all produksjonskode utenom eksplisitte unntak.
// Dekningen verifiseres på `check`, og bygget feiler hvis terskelen ikke holdes.
// Rapporter genereres ved å kjøre koverHtmlReport eller koverXmlReport.
kover {
    reports {
        total {
            filters {
                excludes {
                    // Det eneste som står igjen her er main/start, altså oppstarten av selve Netty-serveren.
                    // Innholdet — jobbene, Kafka-consumerne og Ktor-oppsettet — ligger i Bakgrunnsprosesser.kt og KtorSetup.kt, som testene kjører direkte.
                    // start() kan ikke dekkes uten å faktisk starte en blokkerende server; LokalMain kjører den samme rutinen manuelt.
                    classes("no.nav.tiltakspenger.soknad.api.ApplicationKt*")
                }
            }
            verify {
                onCheck = true
                rule("all produksjonskode utenom eksplisitte unntak skal ha 100 % linjedekning") {
                    bound {
                        minValue = 100
                        coverageUnits = CoverageUnit.LINE
                        aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                    }
                }
            }
        }
    }
}

tasks.named("koverXmlReport") {
    val xmlReport = layout.buildDirectory.file("reports/kover/report.xml")
    doLast {
        val xml = xmlReport.get().asFile
        val classCount = xml.readText().split("<class ").size - 1
        if (classCount == 0) throw GradleException("Kover-rapporten inneholder ingen klasser – ekskluderingsfilteret er trolig for grådig.")
    }
}

spotless {
    kotlin {
        ktlint()
            .editorConfigOverride(
                mapOf(
                    "ktlint_standard_max-line-length" to "off",
                    // Fjerner ubrukte importer automatisk i spotlessApply, og feiler i spotlessCheck.
                    // Eksplisitt aktivert fordi default code style (intellij_idea) deaktiverer den.
                    "ktlint_standard_no-unused-imports" to "enabled",
                    "ktlint_standard_function-signature" to "disabled",
                    "ktlint_standard_function-expression-body" to "disabled",
                ),
            )
    }
}

tasks {
    kotlin {
        jvmToolchain(25)
        compilerOptions {
            freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
        }
    }

    test {
        // JUnit 5-støtte
        useJUnitPlatform()
        // https://phauer.com/2018/best-practices-unit-testing-kotlin/
        systemProperty("junit.jupiter.testinstance.lifecycle.default", "per_class")

        testLogging {
            // Vi logger bare feilede og hoppede tester når Gradle kjører.
            events("skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    register("checkFlywayMigrationNames") {
        val sqlMigrationDir = project.file("src/main/resources/db/migration")
        val kotlinMigrationDir = project.file("src/main/kotlin/db/migration")
        doLast {
            val sqlFiles =
                sqlMigrationDir
                    .walk()
                    .filter { it.isFile && it.extension == "sql" }
                    .toList()

            val invalidSqlFiles =
                sqlFiles
                    .filterNot { it.name.matches(Regex("V[0-9]+__[a-zA-Z0-9][\\w]+\\.sql")) }
                    .map { it.name }

            if (invalidSqlFiles.isNotEmpty()) {
                throw GradleException("Invalid SQL migration filenames:\n${invalidSqlFiles.joinToString("\n")}")
            }
            val kotlinFiles =
                kotlinMigrationDir
                    .walk()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                    .toList()

            val invalidKotlinFiles =
                kotlinFiles
                    .filterNot { it.name.matches(Regex("V[0-9]+__[a-zA-Z0-9][\\w]+\\.(kt|java)")) }
                    .map { it.name }

            if (invalidKotlinFiles.isNotEmpty()) {
                throw GradleException("Invalid Kotlin/Java migration filenames:\n${invalidKotlinFiles.joinToString("\n")}")
            }

            // Sjekk for dupliserte versjoner på tvers av ALLE migreringstyper
            val allFiles = sqlFiles + kotlinFiles
            val duplicateVersions =
                allFiles
                    .mapNotNull {
                        it.name
                            .split("__")
                            .firstOrNull()
                            ?.removePrefix("V")
                            ?.toIntOrNull()
                    }.groupBy { it }
                    .filter { it.value.size > 1 }
                    .keys

            if (duplicateVersions.isNotEmpty()) {
                throw GradleException(
                    "Duplicate version numbers found:\n${duplicateVersions.joinToString("\n") { "Version $it is used multiple times" }}",
                )
            }

            println("All migration filenames are valid and version numbers are unique.")
        }
    }

    register<Copy>("gitHooks") {
        group = "git hooks"
        description = "Installerer git-hooks fra .gitHooks/ til .git/hooks/."
        from(file(".gitHooks"))
        into(file(".git/hooks"))
        filePermissions { unix("rwxr-xr-x") }
    }
}

// --- Ingen andre HTTP-klienter enn libs sin httpklient -------------------------
// Konsist-reglene (IngenAndreHttpKlienter) dekker det vi selv skriver og deklarerer.
// Denne dekker det siste hullet: en klient som kommer inn transitivt gjennom en annen
// avhengighet, uten at den står i noen import eller i denne fila.
//
// Ktor-klienten står bevisst IKKE på lista, og skal ikke legges til: `ktor-server-auth`
// eksponerer `ktor-client-core` som `api` (OAuth-provideren bruker den), så den ligger på
// både compile- og runtime-classpathen så lenge vi bruker ktor sin server-auth. Ktor-klienten
// håndheves derfor i kilden (konsist-regelen) og i byggfila, ikke her.
val verifiserHttpKlienter =
    tasks.register("verifiserHttpKlienter") {
        group = "verification"
        description = "Feiler hvis en annen HTTP-klient enn libs sin httpklient ligger på runtime-classpathen."
        // Lista ligger inne i tasken, ikke som script-val: configuration cache kan ikke
        // serialisere referanser til byggskript-objekter fanget i doLast.
        val forbudteHttpKlienter =
            listOf(
                "com.squareup.okhttp3",
                "com.squareup.retrofit2",
                "org.apache.httpcomponents",
                "com.github.kittinunf.fuel",
                "com.konghq:unirest",
                "io.vertx:vertx-web-client",
                "org.http4k:http4k-client",
                "io.github.openfeign",
            )
        val artefakter = configurations.named("runtimeClasspath").get().incoming.artifacts
        // Filene som input gir Gradle task-avhengighetene: uten dem kan ikke artefaktene slås opp
        // før jar-taskene til et inkludert bygg har kjørt (composite build mot libs).
        inputs.files(artefakter.artifactFiles).withPropertyName("runtimeClasspath")
        val runtimeKomponenter =
            artefakter.resolvedArtifacts
                .map { liste -> liste.map { artefakt -> artefakt.id.componentIdentifier.displayName } }
        doLast {
            val funn = runtimeKomponenter.get().filter { komponent -> forbudteHttpKlienter.any { it in komponent } }
            if (funn.isNotEmpty()) {
                throw GradleException(
                    "Andre HTTP-klienter enn libs sin httpklient på runtime-classpathen:\n" +
                        funn.distinct().sorted().joinToString("\n") { "- $it" },
                )
            }
        }
    }

tasks.named("check") { dependsOn(verifiserHttpKlienter) }
