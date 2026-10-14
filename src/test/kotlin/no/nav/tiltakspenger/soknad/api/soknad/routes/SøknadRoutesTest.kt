package no.nav.tiltakspenger.soknad.api.soknad.routes

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.tiltakspenger.libs.json.objectMapper
import no.nav.tiltakspenger.soknad.api.soknad.Applikasjonseier
import no.nav.tiltakspenger.soknad.api.soknad.ManueltRegistrertBarn
import no.nav.tiltakspenger.soknad.api.soknad.Oppholdssted
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import no.nav.tiltakspenger.soknad.api.soknad.RegistrertBarn
import no.nav.tiltakspenger.soknad.api.soknad.Tiltak
import no.nav.tiltakspenger.soknad.api.soknad.validering.barnetillegg
import no.nav.tiltakspenger.soknad.api.soknad.validering.spørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.soknad.validering.toJsonString
import no.nav.tiltakspenger.soknad.api.testutils.TestApplicationContext
import no.nav.tiltakspenger.soknad.api.testutils.enkelPdf
import no.nav.tiltakspenger.soknad.api.testutils.medTestApplikasjon
import no.nav.tiltakspenger.soknad.api.testutils.nyttTestFødselsnummer
import no.nav.tiltakspenger.soknad.api.testutils.postSøknad
import no.nav.tiltakspenger.soknad.api.vedlegg.MAKS_ANTALL_VEDLEGG
import no.nav.tiltakspenger.soknad.api.vedlegg.MAKS_FILSTØRRELSE_BYTES
import org.junit.jupiter.api.Test
import tools.jackson.databind.node.ObjectNode
import java.time.LocalDate

class SøknadRoutesTest {
    private val testFødselsnummer = nyttTestFødselsnummer()

    @Test
    fun `post uten token gir 401`() {
        medTestApplikasjon {
            postSøknad(token = null).status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post med ukjent token gir 401`() {
        medTestApplikasjon {
            postSøknad(token = "ugyldigtoken").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post med token som har for lavt acr-nivå gir 401`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer, acr = "Level3")

            postSøknad(token).status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `post med gyldig søknad uten vedlegg gir 201 og lagrer søknaden`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)

            postSøknad(token).status shouldBe HttpStatusCode.Created

            val lagret = tac.søknadRepo.hentBrukersSøknader(testFødselsnummer, Applikasjonseier.Tiltakspenger).single()
            lagret.fnr shouldBe testFødselsnummer
            lagret.acr shouldBe "idporten-loa-high"
            lagret.vedlegg shouldBe emptyList()
        }
    }

    @Test
    fun `post med vedlegg virussjekker vedlegget og lagrer det`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.clamAvTransport.leggIKøJson("""[{"Filename":"vedlegg-1.pdf","Result":"OK"}]""")

            postSøknad(token, vedlegg = listOf(enkelPdf())).status shouldBe HttpStatusCode.Created

            tac.clamAvTransport.mottatteKall.single().bodyTekst.contains("vedlegg-1.pdf") shouldBe true
            val lagret = tac.søknadRepo.alle.single()
            lagret.vedlegg.single().filnavn shouldBe "vedlegg-1.pdf"
            lagret.vedlegg.single().contentType shouldBe "application/pdf"
        }
    }

    @Test
    fun `post med maks antall vedlegg går gjennom`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            val filnavn = (1..MAKS_ANTALL_VEDLEGG).map { "vedlegg-$it.pdf" }
            tac.clamAvTransport.leggIKøJson(filnavn.joinToString(",", "[", "]") { """{"Filename":"$it","Result":"OK"}""" })

            postSøknad(token, vedlegg = List(MAKS_ANTALL_VEDLEGG) { enkelPdf() }).status shouldBe HttpStatusCode.Created

            tac.søknadRepo.alle.single().vedlegg.map { it.filnavn } shouldBe filnavn
        }
    }

    @Test
    fun `post med flere vedlegg enn maksgrensen gir 400 og virussjekker ingenting`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)

            postSøknad(token, vedlegg = List(MAKS_ANTALL_VEDLEGG + 1) { enkelPdf() }).status shouldBe HttpStatusCode.BadRequest

            tac.clamAvTransport.mottatteKall shouldBe emptyList()
            tac.søknadRepo.alle shouldBe emptyList()
            tac.metricsCollector.antallUgyldigeSøknaderCounter.count() shouldBe 1.0
        }
    }

    @Test
    fun `post med vedlegg over maks filstørrelse gir 400 og virussjekker ingenting`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            // Tika kjenner igjen PDF på de første bytene, så vi padder en ekte pdf i stedet for å bygge en på over 10 MB.
            val forStortVedlegg = enkelPdf() + ByteArray(MAKS_FILSTØRRELSE_BYTES)

            postSøknad(token, vedlegg = listOf(forStortVedlegg)).status shouldBe HttpStatusCode.BadRequest

            tac.clamAvTransport.mottatteKall shouldBe emptyList()
            tac.søknadRepo.alle shouldBe emptyList()
            tac.metricsCollector.antallUgyldigeSøknaderCounter.count() shouldBe 1.0
        }
    }

    @Test
    fun `post med vedlegg som inneholder skadevare gir 400 og lagrer ingenting`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.clamAvTransport.leggIKøJson("""[{"Filename":"vedlegg-1.pdf","Result":"FOUND"}]""")

            postSøknad(token, vedlegg = listOf(enkelPdf())).status shouldBe HttpStatusCode.BadRequest

            tac.søknadRepo.alle shouldBe emptyList()
        }
    }

    @Test
    fun `post gir 500 når virusskanningen av en fil feiler`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.clamAvTransport.leggIKøJson("""[{"Filename":"vedlegg-1.pdf","Result":"ERROR"}]""")

            postSøknad(token, vedlegg = listOf(enkelPdf())).status shouldBe HttpStatusCode.InternalServerError

            tac.søknadRepo.alle shouldBe emptyList()
        }
    }

    @Test
    fun `post gir 500 når kallet til ClamAV feiler`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            tac.clamAvTransport.leggIKøStatus(503, "clamav er nede")

            postSøknad(token, vedlegg = listOf(enkelPdf())).status shouldBe HttpStatusCode.InternalServerError

            tac.søknadRepo.alle shouldBe emptyList()
        }
    }

    @Test
    fun `post gir 500 når lagring av søknaden feiler`() {
        val tac = TestApplicationContext()
        tac.søknadRepo.kastVedLagring = RuntimeException("databasen er nede")

        medTestApplikasjon(tac) {
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)

            postSøknad(token).status shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `post uten søknad-part gir 400`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)

            postSøknad(token, søknadJson = null).status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `post med ukjent form-item gir 400`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)

            postSøknad(token, søknadJson = null, ukjentFormItem = true).status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `post med søknad som ikke validerer gir 400`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            // Fra-dato etter til-dato: brytes av valideringen i taInnSøknadSomMultipart.
            val ugyldigTiltak = Tiltak(
                aktivitetId = "123",
                periode = Periode(fra = LocalDate.of(2025, 1, 2), til = LocalDate.of(2025, 1, 1)),
                arenaRegistrertPeriode = null,
                arrangør = "test",
                type = "test",
                typeNavn = "test",
                gjennomforingId = null,
                visningsnavn = null,
            )
            val ugyldig = spørsmålsbesvarelser(tiltak = ugyldigTiltak)

            postSøknad(token, søknadJson = ugyldig.toJsonString()).status shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `post med ja på barnetillegg og barn med oppholdssted gir 201 og lagrer svarene`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            val søknad = spørsmålsbesvarelser(
                barnetillegg = barnetillegg(
                    søkerOmBarnetillegg = true,
                    manueltRegistrerteBarnSøktBarnetilleggFor = listOf(manueltBarn(Oppholdssted.IKKE_EØS_LAND)),
                    registrerteBarnSøktBarnetilleggFor = listOf(registrertBarn(Oppholdssted.ANNET_EØS_LAND)),
                ),
            )

            postSøknad(token, søknadJson = søknad.toJsonString()).status shouldBe HttpStatusCode.Created

            val barnetillegg = tac.søknadRepo.alle.single().søknadSpm.barnetillegg
            barnetillegg.søkerOmBarnetillegg shouldBe true
            barnetillegg.registrerteBarnSøktBarnetilleggFor.single().oppholdssted shouldBe Oppholdssted.ANNET_EØS_LAND
            barnetillegg.manueltRegistrerteBarnSøktBarnetilleggFor.single().oppholdssted shouldBe Oppholdssted.IKKE_EØS_LAND
        }
    }

    @Test
    fun `post med nei på barnetillegg og ingen barn gir 201`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            val søknad = spørsmålsbesvarelser(
                barnetillegg = barnetillegg(
                    søkerOmBarnetillegg = false,
                    manueltRegistrerteBarnSøktBarnetilleggFor = emptyList(),
                    registrerteBarnSøktBarnetilleggFor = emptyList(),
                ),
            )

            postSøknad(token, søknadJson = søknad.toJsonString()).status shouldBe HttpStatusCode.Created

            tac.søknadRepo.alle.single().søknadSpm.barnetillegg.søkerOmBarnetillegg shouldBe false
        }
    }

    @Test
    fun `post med ja på barnetillegg uten barn gir 400 og lagrer ingenting`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            val søknad = spørsmålsbesvarelser(
                barnetillegg = barnetillegg(
                    søkerOmBarnetillegg = true,
                    manueltRegistrerteBarnSøktBarnetilleggFor = emptyList(),
                    registrerteBarnSøktBarnetilleggFor = emptyList(),
                ),
            )

            postSøknad(token, søknadJson = søknad.toJsonString()).status shouldBe HttpStatusCode.BadRequest
            tac.søknadRepo.alle shouldBe emptyList()
        }
    }

    @Test
    fun `post med nei på barnetillegg og barn gir 400 og lagrer ingenting`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            val søknad = spørsmålsbesvarelser(
                barnetillegg = barnetillegg(
                    søkerOmBarnetillegg = false,
                    manueltRegistrerteBarnSøktBarnetilleggFor = emptyList(),
                    registrerteBarnSøktBarnetilleggFor = listOf(registrertBarn(Oppholdssted.NORGE)),
                ),
            )

            postSøknad(token, søknadJson = søknad.toJsonString()).status shouldBe HttpStatusCode.BadRequest
            tac.søknadRepo.alle shouldBe emptyList()
        }
    }

    @Test
    fun `post med barn uten noe svar om oppholdssted gir 400 og lagrer ingenting`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            val søknad = spørsmålsbesvarelser(
                barnetillegg = barnetillegg(
                    søkerOmBarnetillegg = true,
                    manueltRegistrerteBarnSøktBarnetilleggFor = emptyList(),
                    registrerteBarnSøktBarnetilleggFor = listOf(registrertBarn(Oppholdssted.NORGE)),
                ),
            )
            val json = objectMapper.readTree(søknad.toJsonString()) as ObjectNode
            val barn = json["barnetillegg"]["registrerteBarnSøktBarnetilleggFor"][0] as ObjectNode
            barn.remove(listOf("oppholdssted", "oppholdInnenforEøs"))

            postSøknad(token, søknadJson = json.toString()).status shouldBe HttpStatusCode.BadRequest
            tac.søknadRepo.alle shouldBe emptyList()
        }
    }

    @Test
    fun `post med barn der oppholdssted og oppholdInnenforEøs motsier hverandre gir 400`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)
            val søknad = spørsmålsbesvarelser(
                barnetillegg = barnetillegg(
                    søkerOmBarnetillegg = true,
                    manueltRegistrerteBarnSøktBarnetilleggFor = emptyList(),
                    registrerteBarnSøktBarnetilleggFor = listOf(registrertBarn(Oppholdssted.IKKE_EØS_LAND)),
                ),
            )
            val json = objectMapper.readTree(søknad.toJsonString()) as ObjectNode
            (json["barnetillegg"]["registrerteBarnSøktBarnetilleggFor"][0] as ObjectNode).put("oppholdInnenforEøs", true)

            postSøknad(token, søknadJson = json.toString()).status shouldBe HttpStatusCode.BadRequest
            tac.søknadRepo.alle shouldBe emptyList()
        }
    }

    @Test
    fun `post med søknad som ikke er gyldig json gir 400`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)

            postSøknad(token, søknadJson = "{ikke json").status shouldBe HttpStatusCode.BadRequest
            tac.søknadRepo.alle shouldBe emptyList()
        }
    }

    private fun registrertBarn(oppholdssted: Oppholdssted) = RegistrertBarn(
        fnr = nyttTestFødselsnummer(),
        fornavn = "Fornavn",
        mellomnavn = null,
        etternavn = "Etternavn",
        fødselsdato = LocalDate.of(2020, 1, 1),
        oppholdInnenforEøs = null,
        oppholdssted = oppholdssted,
    )

    private fun manueltBarn(oppholdssted: Oppholdssted) = ManueltRegistrertBarn(
        fornavn = "Manuelt",
        mellomnavn = null,
        etternavn = "Barn",
        fødselsdato = LocalDate.of(2021, 1, 1),
        oppholdInnenforEøs = null,
        oppholdssted = oppholdssted,
    )

    @Test
    fun `post med vedlegg av ikke-støttet filtype gir 500`() {
        medTestApplikasjon { tac ->
            val token = tac.texasClient.leggTilBrukertoken(testFødselsnummer)

            val response = postSøknad(token, vedlegg = listOf("ikke en pdf".toByteArray()))

            response.status shouldBe HttpStatusCode.InternalServerError
            tac.søknadRepo.alle shouldBe emptyList()
        }
    }
}
