package no.nav.tiltakspenger.soknad.api.pdl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.tiltakspenger.soknad.api.pdl.client.dto.SøkerRespons
import org.junit.jupiter.api.Test

class SøkerFraPDLTest {

    @Test
    fun mappe() {
        val mapper = JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .addModule(JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build()

        val respons: SøkerRespons = mapper.readValue(json)
    }

    private val json = """
        {"data":{"hentPerson":{"adressebeskyttelse":[],"forelderBarnRelasjon":[{"relatertPersonsIdent":"21921699163","relatertPersonsRolle":"BARN","folkeregistermetadata":{"aarsak":null,"ajourholdstidspunkt":"2020-12-23T10:18:16","gyldighetstidspunkt":"2016-12-21T00:00","kilde":"KILDE_DSF","opphoerstidspunkt":null,"sekvens":null},"metadata":{"endringer":[{"kilde":"KILDE_DSF","registrert":"2021-02-22T16:01:36","registrertAv":"Folkeregisteret","systemkilde":"FREG","type":"OPPRETT"}],"master":"FREG","opplysningsId":"5a65f478-9123-4b70-89d8-4df80eb370cd","historisk":false}},{"relatertPersonsIdent":"01905397180","relatertPersonsRolle":"FAR","folkeregistermetadata":{"aarsak":"Patch","ajourholdstidspunkt":"2022-03-01T14:27:10","gyldighetstidspunkt":"1981-07-23T01:00","kilde":"Synutopia","opphoerstidspunkt":null,"sekvens":null},"metadata":{"endringer":[{"kilde":"KILDE_DSF","registrert":"2021-02-22T16:01:36","registrertAv":"Folkeregisteret","systemkilde":"FREG","type":"OPPRETT"},{"kilde":"Synutopia","registrert":"2022-03-01T14:27:22","registrertAv":"Folkeregisteret","systemkilde":"FREG","type":"KORRIGER"}],"master":"FREG","opplysningsId":"6fa0ece2-1d5e-4718-808d-85f46ee04ddf","historisk":false}},{"relatertPersonsIdent":"21881298055","relatertPersonsRolle":"BARN","folkeregistermetadata":{"aarsak":"Patch","ajourholdstidspunkt":"2022-02-08T09:22:15","gyldighetstidspunkt":"2012-08-21T01:00","kilde":"Synutopia","opphoerstidspunkt":null,"sekvens":null},"metadata":{"endringer":[{"kilde":"KILDE_DSF","registrert":"2021-02-22T16:01:36","registrertAv":"Folkeregisteret","systemkilde":"FREG","type":"OPPRETT"},{"kilde":"Synutopia","registrert":"2022-02-08T09:22:20","registrertAv":"Folkeregisteret","systemkilde":"FREG","type":"KORRIGER"}],"master":"FREG","opplysningsId":"23d6fa6e-3f6e-40d0-809a-e0212aded0ed","historisk":false}},{"relatertPersonsIdent":"19831497346","relatertPersonsRolle":"BARN","folkeregistermetadata":{"aarsak":null,"ajourholdstidspunkt":"2020-12-23T10:18:16","gyldighetstidspunkt":"2014-03-19T00:00","kilde":"KILDE_DSF","opphoerstidspunkt":null,"sekvens":null},"metadata":{"endringer":[{"kilde":"KILDE_DSF","registrert":"2021-02-22T16:01:36","registrertAv":"Folkeregisteret","systemkilde":"FREG","type":"OPPRETT"}],"master":"FREG","opplysningsId":"6b0878f0-d8ae-42f5-aecd-401f04456b44","historisk":false}},{"relatertPersonsIdent":"11835397264","relatertPersonsRolle":"MOR","folkeregistermetadata":{"aarsak":"Patch","ajourholdstidspunkt":"2022-03-01T14:27:10","gyldighetstidspunkt":"1981-07-23T01:00","kilde":"Synutopia","opphoerstidspunkt":null,"sekvens":null},"metadata":{"endringer":[{"kilde":"KILDE_DSF","registrert":"2021-02-22T16:01:36","registrertAv":"Folkeregisteret","systemkilde":"FREG","type":"OPPRETT"},{"kilde":"Synutopia","registrert":"2022-03-01T14:27:22","registrertAv":"Folkeregisteret","systemkilde":"FREG","type":"KORRIGER"}],"master":"FREG","opplysningsId":"f85629e7-5216-4f59-bb76-72fa6e1a29e5","historisk":false}}],"navn":[{"fornavn":"FIN","mellomnavn":null,"etternavn":"LEOPARD","folkeregistermetadata":{"aarsak":"Patch","ajourholdstidspunkt":"2022-02-07T08:02:57","gyldighetstidspunkt":"2022-02-07T08:02:57","kilde":"Synutopia","opphoerstidspunkt":null,"sekvens":null},"metadata":{"endringer":[{"kilde":"Synutopia","registrert":"2022-02-07T08:03:07","registrertAv":"Folkeregisteret","systemkilde":"FREG","type":"OPPRETT"}],"master":"FREG","opplysningsId":"ec153b46-6d0c-488e-a0f3-ff2901a9b3bd","historisk":false}}],"doedsfall":[],"foedselsdato":[]}},"extensions":{"warnings":[{"query":"hentPerson","id":"behandlingskatalogen","message":"Behandling mangler opplysningstyper","details":{"missing":["DOEDSFALL_V1"]}}]}}
    """.trimIndent()
}
