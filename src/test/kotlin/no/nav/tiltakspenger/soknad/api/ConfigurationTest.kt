package no.nav.tiltakspenger.soknad.api

import com.natpryce.konfig.Key
import com.natpryce.konfig.stringType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotEndWith
import org.junit.jupiter.api.Test

/**
 * Cluster-navnet er det eneste som skiller miljøene, og det tas inn som parameter.
 * Dermed kan DEV- og PROD-grenene testes uten å mutere JVM-global systemtilstand som deles med andre tester.
 */
class ConfigurationTest {
    @Test
    fun `cluster-navnet bestemmer profilen`() {
        Configuration.profilFor("dev-gcp") shouldBe Profile.DEV
        Configuration.profilFor("prod-gcp") shouldBe Profile.PROD
        Configuration.profilFor(null) shouldBe Profile.LOCAL
        Configuration.profilFor("noe-annet") shouldBe Profile.LOCAL
    }

    @Test
    fun `hver profil velger sitt eget verdisett`() {
        Configuration.config(Profile.DEV)[Key("application.profile", stringType)] shouldBe "DEV"
        Configuration.config(Profile.PROD)[Key("application.profile", stringType)] shouldBe "PROD"
        Configuration.config(Profile.LOCAL)[Key("application.profile", stringType)] shouldBe "LOCAL"
    }

    @Test
    fun `utenfor Nais kjører vi på den lokale profilen`() {
        Configuration.applicationProfile() shouldBe Profile.LOCAL
        Configuration.isNais() shouldBe false
        Configuration.isProd() shouldBe false
        Configuration.isLocalOrDev() shouldBe true
    }

    @Test
    fun `lokale verdier finnes for alt appen trenger ved oppstart`() {
        Configuration.httpPort() shouldBe 8080
        Configuration.logbackConfigurationFile() shouldBe "logback.local.xml"
        Configuration.electorPath() shouldContain "localhost"
        Configuration.database().url shouldContain "jdbc:postgresql://"

        Configuration.pdlUrl shouldContain "http"
        Configuration.pdlScope shouldBe "localhost"
        Configuration.dokarkivUrl shouldContain "http"
        Configuration.dokarkivScope shouldBe "localhost"
        Configuration.saksbehandlingApiUrl shouldContain "http"
        Configuration.saksbehandlingApiScope shouldBe "localhost"
        Configuration.pdfgenrsUrl shouldContain "http"
        Configuration.avUrl shouldContain "http"
        Configuration.tiltakshistorikkUrl shouldContain "http"
        Configuration.tiltakshistorikkScope shouldBe "localhost"
        Configuration.naisTokenIntrospectionEndpoint shouldContain "http"
        Configuration.naisTokenEndpoint shouldContain "http"
        Configuration.tokenExchangeEndpoint shouldContain "http"
        Configuration.identhendelseTopic shouldBe "tpts.identhendelse-v1"
    }

    /**
     * `PdlIdentklient` fra libs legger på `/graphql` selv, mens miljøvariabelen peker rett på GraphQL-endepunktet.
     * Stripper vi ikke suffikset, kaller den `…/graphql/graphql`.
     */
    @Test
    fun `PDL-basen ender aldri på graphql-suffikset`() {
        Configuration.pdlBaseUrl shouldNotEndWith "/graphql"
        // I dev og prod peker miljøvariabelen på GraphQL-endepunktet og strippes; LOCAL-defaulten har ikke suffikset, så den står urørt.
        Configuration.pdlBaseUrl shouldBe Configuration.pdlUrl
    }

    @Test
    fun `sikkerlogg-lenkeverdiene er null utenfor Nais`() {
        Configuration.naisAppName shouldBe null
        Configuration.gcpTeamProjectId shouldBe null
    }
}
