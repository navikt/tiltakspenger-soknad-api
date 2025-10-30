package no.nav.tiltakspenger.soknad.api.tiltak

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals

internal class TiltakServiceTest {

    private val tiltakspengerTiltakClient = mockk<TiltakspengerTiltakClient>()

    private val tiltakService = TiltakService(
        tiltakspengerTiltakClient = tiltakspengerTiltakClient,
    )

    @Test
    fun `tiltaksarrangør maskeres når maskerArrangørnavn=true`() {
        runBlocking {
            coEvery { tiltakspengerTiltakClient.fetchTiltak(any()) } returns Result.success(
                mockTiltakspengerTiltakResponsDTO(),
            )

            val tiltak = tiltakService.hentTiltak("subjectToken", true)
            assertEquals(tiltak.first().arrangør, "")
        }
    }

    @Test
    fun `man får all informasjon om tiltaket når maskerArrangørnavn=false`() {
        val arrangørNavn = "Arrangør AS"
        runBlocking {
            coEvery { tiltakspengerTiltakClient.fetchTiltak(any()) } returns Result.success(
                mockTiltakspengerTiltakResponsDTO(arrangørNavn),
            )

            val tiltak = tiltakService.hentTiltak("subjectToken", false)
            assertEquals(tiltak.first().arrangør, arrangørNavn)
        }
    }

    @Test
    fun `ved feil mot tiltakspenger-arena kastes en IllegalStateException`() {
        runBlocking {
            assertThrows<IllegalStateException> {
                coEvery { tiltakspengerTiltakClient.fetchTiltak(any()) } returns Result.failure(IllegalStateException())
                tiltakService.hentTiltak("subjectToken", false)
            }.also {
                assertEquals(it.message, "Noe gikk galt under kall til tiltakspenger-tiltak")
            }
        }
    }

    private fun mockTiltakspengerTiltakResponsDTO(arrangør: String = "Arrangør AS") =
        listOf(
            TiltakResponsDTO.TiltakDTO(
                id = "123456",
                gjennomforing = TiltakResponsDTO.GjennomføringDTO(
                    id = "123456",
                    arenaKode = TiltakResponsDTO.TiltakType.ABIST,
                    typeNavn = "typenavn",
                    arrangørnavn = arrangør,
                    deltidsprosent = 100.0,
                ),
                deltakelseFom = null,
                deltakelseTom = null,
                deltakelseStatus = TiltakResponsDTO.DeltakerStatusDTO.DELTAR,
                deltakelseDagerUke = null,
                deltakelseProsent = null,
                kilde = "Komet",
                registrertDato = LocalDateTime.now(),
            ),
        )
}
