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

Appen kan enten kjøres opp lokalt ved å kjøre opp `main()` i `Application.kt` fra f.eks. IntelliJ, eller ved å bruke docker-compose oppsettet
som ligger i [meta-repoet](https://github.com/navikt/tiltakspenger) til team tiltakspenger. Compose-oppsettet til søknaden
kjører også opp [tiltakspenger-soknad-mock-api](https://github.com/navikt/tiltakspenger-soknad-mock-api), som er skreddersydd for
å mocke ut andre api-er som dette api-et er avhengig av for å fungere i utviklingsmiljø.

Eksempel på miljøvariabler som kan settes i en Run Configuration for å kjøre opp appen fra IntelliJ mot kjørende Compose-oppsett:

```
AV_ENDPOINT_URL=http://localhost:8484/av
AZURE_APP_CLIENT_ID=mocked_client_id
AZURE_APP_CLIENT_SECRET=mocked_secret
AZURE_APP_WELL_KNOWN_URL=http://host.docker.internal:6969/azure/.well-known/openid-configuration
DOKARKIV_AUDIENCE=mock_audience
DOKARKIV_ENDPOINT_URL=http://localhost:8484
DOKARKIV_SCOPE=mock_scope
PDF_ENDPOINT_URL=http://localhost:8085
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
