package no.nav.tiltakspenger.soknad.api.soknad.routes

import io.ktor.http.ContentDisposition
import io.ktor.http.HeaderValueParam
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.append
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.utils.io.core.Input
import io.ktor.utils.io.core.endOfInput
import io.ktor.utils.io.jvm.nio.toByteReadChannel
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.asByteChannel
import no.nav.tiltakspenger.soknad.api.soknad.Introduksjonsprogram
import no.nav.tiltakspenger.soknad.api.soknad.Periode
import no.nav.tiltakspenger.soknad.api.soknad.validering.spørsmålsbesvarelser
import no.nav.tiltakspenger.soknad.api.soknad.validering.toJsonString
import no.nav.tiltakspenger.soknad.api.util.Detect
import no.nav.tiltakspenger.soknad.api.util.sjekkContentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class SoknadRequestMapperTest {
    private val gyldigSpørsmålsbesvarelser = spørsmålsbesvarelser()

    class MockMultiPartData(private val partDataList: MutableList<PartData>) : MultiPartData {
        override suspend fun readPart(): PartData? {
            return if (partDataList.isNotEmpty()) partDataList.removeLast() else null
        }
    }

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `taInnSøknadSomMultipart leser inn MultiPartData med gyldig søknad to vedlegg`() {
        mockkStatic("no.nav.tiltakspenger.soknad.api.util.DetectKt")
        every { sjekkContentType(any()) } returns Detect.APPLICATON_PDF

        val buffer1 = Buffer()
        val buffer2 = Buffer()

        val mockMultiPartData = MockMultiPartData(
            mutableListOf(
                PartData.FormItem(
                    gyldigSpørsmålsbesvarelser.toJsonString(),
                    {},
                    Headers.build {
                        append(HttpHeaders.ContentType, "application/json")
                        append(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition("søknad", listOf(HeaderValueParam("name", "søknad"))),
                        )
                    },
                ),
                PartData.FileItem(
                    { buffer1.asByteChannel().toByteReadChannel() },
                    { buffer1.close() },
                    Headers.build {
                        append(HttpHeaders.ContentType, "application/pdf")
                        append(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition("vedlegg", listOf(HeaderValueParam("name", "vedlegg"))),
                        )
                    },
                ),
                PartData.FileItem(
                    { buffer2.asByteChannel().toByteReadChannel() },
                    { buffer2.close() },
                    Headers.build {
                        append(HttpHeaders.ContentType, "application/pdf")
                        append(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition("vedlegg", listOf(HeaderValueParam("name", "vedlegg"))),
                        )
                    },
                ),
            ),
        )

        runBlocking {
            val (søknad, vedlegg) = taInnSøknadSomMultipart(mockMultiPartData)
            assertEquals(søknad.tiltak.aktivitetId, "123")
            assertEquals(vedlegg.size, 2)
        }
    }

    @Test
    fun `taInnSøknadSomMultipart gir feil ved ugyldig søknad`() {
        val input: Input = mockk()
        every { input.endOfInput } returns true
        justRun { input.close() }
        mockkStatic("no.nav.tiltakspenger.soknad.api.util.DetectKt")
        every { sjekkContentType(any()) } returns Detect.APPLICATON_PDF

        val introduksjonsprogram = Introduksjonsprogram(
            deltar = true,
            periode = Periode(
                fra = LocalDate.of(2025, 1, 2),
                til = LocalDate.of(2025, 1, 1),
            ),
        )

        val mockMultiPartData = MockMultiPartData(
            mutableListOf(
                PartData.FormItem(
                    spørsmålsbesvarelser(introduksjonsprogram = introduksjonsprogram).toJsonString(),
                    {},
                    Headers.build {
                        append(HttpHeaders.ContentType, "application/json")
                        append(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition("søknad", listOf(HeaderValueParam("name", "søknad"))),
                        )
                    },
                ),
            ),
        )

        runBlocking {
            assertThrows<RequestValidationException> { taInnSøknadSomMultipart(mockMultiPartData) }
        }
    }
}
