tiltakspenger-soknad-api
================

API-tjeneste for [Tiltakspengesøknaden](https://github.com/navikt/tiltakspenger-soknad)

En del av satsningen ["Flere i arbeid – P4"](https://memu.no/artikler/stor-satsing-skal-fornye-navs-utdaterte-it-losninger-og-digitale-verktoy/)

# Komme i gang
## Forutsetninger
- [JDK](https://jdk.java.net/)
- [Kotlin](https://kotlinlang.org/)
- [Gradle](https://gradle.org/) brukes som byggeverktøy og er inkludert i oppsettet

For hvilke versjoner som brukes, [se byggefilen](build.gradle.kts)

## Kjøre opp lokalt

Kjør `main()` i `LokalMain.kt` (i testkildene).
Det eneste som må kjøre ved siden av, er postgres — og den starter `LokalMain` for deg.

Svarer ikke databasen på porten fra `Configuration` (5436), letes `docker-compose-soknad.yml` opp oppover fra repoet (den ligger i monorepo-rota), tjenesten `postgresSoknad` startes med `docker compose up -d`, og vi venter til den tar imot tilkoblinger.
Kjører den allerede, gjøres ingenting.
Porten og det navngitte volumet er de samme som før, så dataene dine overlever både omstart av appen og av containeren.
Går noe galt — docker er ikke startet, compose-fila finnes ikke, porten er opptatt av noe annet — får du en feilmelding som sier hva som er galt og hva du gjør med det.

Vil du heller kjøre uten compose-oppsettet, sett miljøvariabelen `LOKAL_DB_MODUS=testcontainers`.
Da starter vi en egen postgres-container via Testcontainers, på en tilfeldig port som appen plukker opp selv.
Databasen er da tom ved hver oppstart med mindre du også setter `TESTCONTAINERS_REUSE_ENABLE=true`.

Oppstartslogikken er delt med de andre appene og ligger i `tiltakspenger-libs:lokal-oppstart` (`startLokalPostgres`).
Vil du fortsatt starte databasen selv, er kommandoen:

```sh
docker compose -f ../docker-compose-soknad.yml up postgresSoknad
```

`LokalMain` kjører nøyaktig samme `start()`-rutine som i drift, men med `LokalApplicationContext`.
Der er hver utgående avhengighet — PDL, tiltak, virussjekk, PDF-generering, journalføring og oversending til saksbehandling-api — byttet ut med sin egen fake, og hvilket som helst token godtas.
Fakene ligger i testkildene ved siden av de andre testhjelperne, og implementerer den samme porten som produksjonsklienten.
Klientene selv er dekket av sine egne tester over `FakeHttpTransport`; lokal kjøring skal bare gi en app som står på egne bein.
Testbrukeren har to barn under 16 år og ett aktivt tiltak, slik at hele søknaden kan fylles ut.

`LokalApplicationContextTest` kjører hele søknadsflyten mot den samme konteksten, slik at oppsettet ikke kan råtne i det stille.

`main()` i `Application.kt` er produksjonsinngangen, og krever at alle de eksterne tjenestene finnes.

Eksempel på miljøvariabler som kan settes i en Run Configuration for å kjøre opp appen fra IntelliJ mot kjørende Compose-oppsett:

```
AV_ENDPOINT_URL=http://localhost:8484/av
AZURE_APP_CLIENT_ID=mocked_client_id
AZURE_APP_CLIENT_SECRET=mocked_secret
AZURE_APP_WELL_KNOWN_URL=http://host.docker.internal:6969/azure/.well-known/openid-configuration
DOKARKIV_AUDIENCE=mock_audience
DOKARKIV_ENDPOINT_URL=http://localhost:8484
DOKARKIV_SCOPE=mock_scope
PDL_ENDPOINT_URL=http://localhost:8484/personalia
PDL_SCOPE=mock_scope
TILTAKSPENGER_ARENA_AUDIENCE=mock_audience
TILTAKSPENGER_ARENA_ENDPOINT_URL=http://localhost:8484
TILTAKSPENGER_TILTAK_AUDIENCE=blabla
TILTAKSPENGER_TILTAK_ENDPOINT_URL=http://localhost:8484
TOKEN_X_CLIENT_ID=localhost:tpts:tiltakspenger-soknad-api
TOKEN_X_PRIVATE_JWK=<din jwk>
TOKEN_X_WELL_KNOWN_URL=http://host.docker.internal:6969/tokendings/.well-known/openid-configuration
```

## Bygging og denslags
For å bygge artifaktene:

```sh
./gradlew build
```

---

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #tiltakspenger-utvikling.
