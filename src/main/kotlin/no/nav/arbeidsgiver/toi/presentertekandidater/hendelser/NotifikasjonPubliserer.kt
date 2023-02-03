package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser


import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.arbeidsgiver.toi.presentertekandidater.log
import no.nav.helse.rapids_rivers.RapidsConnection
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

class NotifikasjonPubliserer(val rapidsConnection: RapidsConnection) {

    fun publiserNotifikasjonForCvDelt(kandidathendelse: Kandidathendelse, cvDeltData: CvDeltData) {
        val tidspunkt = kandidathendelse.tidspunkt.withZoneSameInstant(ZoneId.of("Europe/Oslo"))
        val notifikasjonsId = "${kandidathendelse.stillingsId}-$tidspunkt"

        val melding = Notifikasjonsmelding(
            notifikasjonsId,
            kandidathendelse.stillingsId,
            tidspunkt,
            kandidathendelse.organisasjonsnummer,
            cvDeltData.utførtAvVeilederFornavn,
            cvDeltData.utførtAvVeilederEtternavn,
            cvDeltData.arbeidsgiversEpostadresser,
            cvDeltData.meldingTilArbeidsgiver,
            cvDeltData.stillingstittel
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
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        fun tilJsonMelding() = objectMapper.valueToTree<ObjectNode>(this).apply {
            put("@event_name", "notifikasjon.cv-delt")
        }.toString()
    }
}
