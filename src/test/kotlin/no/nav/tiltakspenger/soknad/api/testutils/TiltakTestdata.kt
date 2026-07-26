package no.nav.tiltakspenger.soknad.api.testutils

import no.nav.tiltakspenger.libs.tiltak.TiltakResponsDTO
import no.nav.tiltakspenger.libs.tiltak.TiltakshistorikkDTO
import java.time.LocalDate

/**
 * Ett tiltakshistorikk-element slik tiltakspenger-tiltak svarer.
 * Køes på [TestApplicationContext.tiltakTransport] i klienttestene, og deles ut direkte av [TiltakKlientFake] ved lokal kjøring.
 */
fun tiltakshistorikk(
    id: String = "123456",
    gjennomforingId: String = "gjennomforing-1",
    arrangør: String = "Testarrangør AS",
    typeNavn: String = "typenavn",
    deltakelseFom: LocalDate? = null,
): TiltakshistorikkDTO = TiltakshistorikkDTO(
    id = id,
    gjennomforing = TiltakshistorikkDTO.GjennomforingDTO(
        id = gjennomforingId,
        arenaKode = TiltakResponsDTO.TiltakTypeDTO.ABOPPF,
        typeNavn = typeNavn,
        arrangornavn = arrangør,
        deltidsprosent = 100.0,
        visningsnavn = "$typeNavn hos $arrangør",
    ),
    deltakelseFom = deltakelseFom,
    deltakelseTom = null,
    deltakelseStatus = TiltakResponsDTO.DeltakerStatusDTO.DELTAR,
    antallDagerPerUke = null,
    kilde = TiltakshistorikkDTO.Kilde.KOMET,
    deltakelseProsent = null,
)
