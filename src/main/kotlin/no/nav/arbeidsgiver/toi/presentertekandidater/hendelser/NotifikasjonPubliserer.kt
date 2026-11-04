package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser


import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.arbeidsgiver.toi.presentertekandidater.log
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

class NotifikasjonPubliserer(val rapidsConnection: RapidsConnection) {

    fun publiserNotifikasjonForCvDelt(tidspunkt: ZonedDateTime, stillingsId: UUID, organisasjonsnummer: String, cvDeltData: CvDeltData, stillingstittel: String) {
        val tidspunkt = tidspunkt.withZoneSameInstant(ZoneId.of("Europe/Oslo"))
        val notifikasjonsId = "$stillingsId-$tidspunkt"

        val melding = Notifikasjonsmelding(
            notifikasjonsId,
            stillingsId,
            tidspunkt,
            organisasjonsnummer,
            cvDeltData.utførtAvVeilederFornavn,
            cvDeltData.utførtAvVeilederEtternavn,
            cvDeltData.arbeidsgiversEpostadresser,
            cvDeltData.meldingTilArbeidsgiver,
            stillingstittel
        )

        rapidsConnection.publish(notifikasjonsId, melding.tilJsonMelding())
        log.info("Har publisert notifikasjonsmelding med notifikasjonsId: $notifikasjonsId")
    }

    data class Notifikasjonsmelding(
        val notifikasjonsId: String,
        val stillingsId: UUID,
        val tidspunktForHendelse: ZonedDateTime,
        val virksomhetsnummer: String,
        val utførtAvVeilederFornavn: String,
        val utførtAvVeilederEtternavn: String,
        val arbeidsgiversEpostadresser: List<String>,
        val meldingTilArbeidsgiver: String,
        val stillingstittel: String
    ) {
        private val objectMapper = jacksonMapperBuilder().build()

        fun tilJsonMelding() = objectMapper.valueToTree<ObjectNode>(this).apply {
            put("@event_name", "notifikasjon.cv-delt")
        }.toString()
    }
}
