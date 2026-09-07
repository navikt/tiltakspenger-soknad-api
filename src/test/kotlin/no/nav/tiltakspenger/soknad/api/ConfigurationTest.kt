package no.nav.tiltakspenger.soknad.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotEndWith
import org.junit.jupiter.api.Test

/**
 * Testmiljøet har ingen `NAIS_CLUSTER_NAME`, så [Configuration] delegerer til [LocalConfig] her.
 * [DevConfig] og [ProdConfig] åpnes direkte; de nais-injiserte miljøvariablene de leser ved initialisering er satt til dummy-verdier i test-tasken.
 */
class ConfigurationTest {
    @Test
    fun `cluster-navnet bestemmer hvilken config som velges`() {
        configForMiljø("dev-gcp") shouldBe DevConfig
        configForMiljø("prod-gcp") shouldBe ProdConfig
        configForMiljø(null) shouldBe LocalConfig
        configForMiljø("noe-annet") shouldBe LocalConfig
    }

    @Test
    fun `utenfor Nais kjører vi på den lokale profilen`() {
        Configuration.environmentProfile shouldBe EnvironmentProfile.LOCAL
        Configuration.isNais() shouldBe false
        Configuration.isProd() shouldBe false
        Configuration.isLocalOrDev() shouldBe true
    }

    @Test
    fun `lokale verdier finnes for alt appen trenger ved oppstart`() {
        Configuration.httpPort shouldBe 8080
        Configuration.logbackConfigurationFile shouldBe "logback.local.xml"
        Configuration.electorPath shouldContain "localhost"
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
        Configuration.tokenIntrospectionEndpoint shouldContain "http"
        Configuration.tokenEndpoint shouldContain "http"
        Configuration.tokenExchangeEndpoint shouldContain "http"
        Configuration.identhendelseTopic shouldBe "tpts.identhendelse-v1"
    }

    /**
     * Verdiene for dev og prod ligger i koden i stedet for i nais-manifestet.
     * Testene under pinner dem slik at en skrivefeil i en url eller et scope feiler her og ikke først i drift.
     * Miljøvariablene de leser (ELECTOR_PATH, DB_JDBC_URL, NAIS_TOKEN_*) er satt til dummy-verdier i test-tasken i build.gradle.kts.
     */
    @Test
    fun `dev-verdiene peker på dev-tjenestene`() {
        DevConfig.environmentProfile shouldBe EnvironmentProfile.DEV
        DevConfig.pdlScope shouldBe "dev-fss:pdl:pdl-api"
        DevConfig.pdlUrl shouldBe "https://pdl-api.dev-fss-pub.nais.io/graphql"
        DevConfig.dokarkivScope shouldBe "dev-fss:teamdokumenthandtering:dokarkiv"
        DevConfig.dokarkivUrl shouldBe "https://dokarkiv-q2.dev-fss-pub.nais.io"
        DevConfig.saksbehandlingApiScope shouldBe "dev-gcp:tpts:tiltakspenger-saksbehandling-api"
        DevConfig.saksbehandlingApiUrl shouldBe "http://tiltakspenger-saksbehandling-api"
        DevConfig.tiltakshistorikkScope shouldBe "dev-gcp:team-mulighetsrommet:tiltakshistorikk"
        DevConfig.tiltakshistorikkUrl shouldBe "http://tiltakshistorikk.team-mulighetsrommet"
        DevConfig.pdfgenrsUrl shouldBe "http://tiltakspenger-pdfgenrs"
        DevConfig.avUrl shouldBe "http://clamav.nais-system/scan"
        DevConfig.identhendelseTopic shouldBe "tpts.identhendelse-v1"
        DevConfig.logbackConfigurationFile shouldBe "logback.xml"
    }

    @Test
    fun `prod-verdiene peker på prod-tjenestene`() {
        ProdConfig.environmentProfile shouldBe EnvironmentProfile.PROD
        ProdConfig.pdlScope shouldBe "prod-fss:pdl:pdl-api"
        ProdConfig.pdlUrl shouldBe "https://pdl-api.prod-fss-pub.nais.io/graphql"
        ProdConfig.dokarkivScope shouldBe "prod-fss:teamdokumenthandtering:dokarkiv"
        ProdConfig.dokarkivUrl shouldBe "https://dokarkiv.prod-fss-pub.nais.io"
        ProdConfig.saksbehandlingApiScope shouldBe "prod-gcp:tpts:tiltakspenger-saksbehandling-api"
        ProdConfig.saksbehandlingApiUrl shouldBe "http://tiltakspenger-saksbehandling-api"
        ProdConfig.tiltakshistorikkScope shouldBe "prod-gcp:team-mulighetsrommet:tiltakshistorikk"
        ProdConfig.tiltakshistorikkUrl shouldBe "http://tiltakshistorikk.team-mulighetsrommet"
        ProdConfig.pdfgenrsUrl shouldBe "http://tiltakspenger-pdfgenrs"
        ProdConfig.avUrl shouldBe "http://clamav.nais-system/scan"
        ProdConfig.identhendelseTopic shouldBe "tpts.identhendelse-v1"
        ProdConfig.logbackConfigurationFile shouldBe "logback.xml"
    }

    /**
     * `PdlIdentklient` fra libs legger på `/graphql` selv, mens pdl-urlen peker rett på GraphQL-endepunktet.
     * Stripper vi ikke suffikset, kaller den `…/graphql/graphql`.
     */
    @Test
    fun `PDL-basen ender aldri på graphql-suffikset`() {
        Configuration.pdlBaseUrl shouldNotEndWith "/graphql"
        // Den lokale urlen har ikke suffikset, så den står urørt; i dev og prod peker urlen på GraphQL-endepunktet og strippes.
        Configuration.pdlBaseUrl shouldBe Configuration.pdlUrl
    }

    @Test
    fun `sikkerlogg-lenkeverdiene er null utenfor Nais`() {
        Configuration.naisAppName shouldBe null
        Configuration.gcpTeamProjectId shouldBe null
    }
}
