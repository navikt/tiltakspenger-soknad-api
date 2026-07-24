package no.nav.tiltakspenger.soknad.api.vedlegg

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.security.InvalidParameterException

class VedleggTest {
    private val vedlegg = Vedlegg(filnavn = "fil.pdf", contentType = "application/pdf", dokument = byteArrayOf(1, 2))

    @Test
    fun `like vedlegg har lik hashCode, ulike har ulik`() {
        vedlegg.hashCode() shouldBe vedlegg.copy(dokument = byteArrayOf(1, 2)).hashCode()
        vedlegg.hashCode() shouldNotBe vedlegg.copy(filnavn = "annen.pdf").hashCode()
        vedlegg shouldBe vedlegg.copy(dokument = byteArrayOf(1, 2))
        vedlegg shouldNotBe vedlegg.copy(dokument = byteArrayOf(3))
    }

    @Test
    fun `serialisering til og fra db-json er hverandres inverser`() {
        listOf(vedlegg).toDbJson().vedleggDbJson() shouldBe listOf(vedlegg)
    }

    @Test
    fun `ugyldig json gir InvalidParameterException`() {
        shouldThrow<InvalidParameterException> { "ikke json".vedleggDbJson() }
    }
}
