package no.nav.tiltakspenger.soknad.api.soknad.routes

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.tiltakspenger.libs.common.SøknadId
import no.nav.tiltakspenger.libs.common.random
import no.nav.tiltakspenger.soknad.api.soknad.Behandlingsresultat
import no.nav.tiltakspenger.soknad.api.soknad.Behandlingsutfall
import no.nav.tiltakspenger.soknad.api.soknad.validering.spørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.soknad.validering.toJsonString
import no.nav.tiltakspenger.soknad.api.testutils.behandlingsutfallJson
import no.nav.tiltakspenger.soknad.api.testutils.medTestApplikasjon
import no.nav.tiltakspenger.soknad.api.testutils.nyttTestFødselsnummer
import no.nav.tiltakspenger.soknad.api.testutils.postBehandlingsutfall
import no.nav.tiltakspenger.soknad.api.testutils.postSøknad
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class BehandlingsutfallRoutesTest {
    @Test
    fun `lagrer utfallet, og bruker kan deretter sende den samme søknaden på nytt`() {
        medTestApplikasjon { tac ->
            val brukertoken = tac.texasClient.leggTilBrukertoken(nyttTestFødselsnummer())
            val søknad = spørsmålsbesvarelser().toJsonString()
            postSøknad(brukertoken, søknadJson = søknad).status shouldBe HttpStatusCode.Created
            postSøknad(brukertoken, søknadJson = søknad).status shouldBe HttpStatusCode.Conflict
            val første = tac.søknadRepo.alle.single()

            postBehandlingsutfall(
                token = tac.texasClient.leggTilAzureToken(),
                body = behandlingsutfallJson(søknadId = første.id, utfall = "AVSLAG", vedtakId = "vedtak_1"),
            ).status shouldBe HttpStatusCode.NoContent

            tac.søknadRepo.hentBehandlingsutfall(første.id) shouldBe Behandlingsutfall(
                resultat = Behandlingsresultat.AVSLAG,
                tidspunkt = LocalDateTime.of(2025, 2, 1, 12, 0),
                vedtakId = "vedtak_1",
            )
            postSøknad(brukertoken, søknadJson = søknad).status shouldBe HttpStatusCode.Created
            tac.søknadRepo.alle.size shouldBe 2
        }
    }

    @Test
    fun `en avbrutt søknad kan sendes på nytt, men ikke etter at den er gjenåpnet`() {
        medTestApplikasjon { tac ->
            val brukertoken = tac.texasClient.leggTilBrukertoken(nyttTestFødselsnummer())
            val azureToken = tac.texasClient.leggTilAzureToken()
            val søknad = spørsmålsbesvarelser().toJsonString()
            postSøknad(brukertoken, søknadJson = søknad).status shouldBe HttpStatusCode.Created
            val første = tac.søknadRepo.alle.single()

            postBehandlingsutfall(
                token = azureToken,
                body = behandlingsutfallJson(søknadId = første.id, utfall = "GJENÅPNET", tidspunkt = "2025-02-02T12:00:00"),
            ).status shouldBe HttpStatusCode.NoContent
            postBehandlingsutfall(
                token = azureToken,
                body = behandlingsutfallJson(søknadId = første.id, utfall = "AVBRUTT", tidspunkt = "2025-02-01T12:00:00"),
            ).status shouldBe HttpStatusCode.NoContent

            // Avbruddet er eldre enn gjenåpningen, så søknaden er fortsatt under behandling.
            tac.søknadRepo.hentBehandlingsutfall(første.id) shouldBe Behandlingsutfall(
                resultat = Behandlingsresultat.GJENÅPNET,
                tidspunkt = LocalDateTime.of(2025, 2, 2, 12, 0),
                vedtakId = null,
            )
            postSøknad(brukertoken, søknadJson = søknad).status shouldBe HttpStatusCode.Conflict

            postBehandlingsutfall(
                token = azureToken,
                body = behandlingsutfallJson(søknadId = første.id, utfall = "AVBRUTT", tidspunkt = "2025-02-03T12:00:00"),
            ).status shouldBe HttpStatusCode.NoContent

            tac.søknadRepo.hentBehandlingsutfall(første.id)!!.resultat shouldBe Behandlingsresultat.AVBRUTT
            postSøknad(brukertoken, søknadJson = søknad).status shouldBe HttpStatusCode.Created
        }
    }

    @Test
    fun `utfall for en søknad vi ikke har gir 404`() {
        medTestApplikasjon { tac ->
            postBehandlingsutfall(
                token = tac.texasClient.leggTilAzureToken(),
                body = behandlingsutfallJson(søknadId = SøknadId.random()),
            ).status shouldBe HttpStatusCode.NotFound
        }
    }

    @Test
    fun `utfall som ikke kan tolkes gir 400`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilAzureToken()

            postBehandlingsutfall(token, body = behandlingsutfallJson(søknadId = SøknadId.random(), utfall = "UKJENT")).status shouldBe HttpStatusCode.BadRequest
            postBehandlingsutfall(token, body = """{"søknadId":"ikke-en-id"}""").status shouldBe HttpStatusCode.BadRequest
            postBehandlingsutfall(token, body = behandlingsutfallJson(søknadId = SøknadId.random(), utfall = "AVBRUTT", vedtakId = "vedtak_1")).status shouldBe HttpStatusCode.BadRequest
            postBehandlingsutfall(token, body = behandlingsutfallJson(søknadId = SøknadId.random(), utfall = "INNVILGELSE", vedtakId = null)).status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `uten token, med brukertoken eller med token for en ansatt avvises kallet`() {
        medTestApplikasjon { tac ->
            val body = behandlingsutfallJson(søknadId = SøknadId.random())

            postBehandlingsutfall(token = null, body = body).status shouldBe HttpStatusCode.Unauthorized
            postBehandlingsutfall(token = tac.texasClient.leggTilBrukertoken(nyttTestFødselsnummer()), body = body).status shouldBe HttpStatusCode.Unauthorized
            postBehandlingsutfall(token = tac.texasClient.leggTilAzureToken(erSystembruker = false), body = body).status shouldBe HttpStatusCode.Forbidden
        }
    }
}
