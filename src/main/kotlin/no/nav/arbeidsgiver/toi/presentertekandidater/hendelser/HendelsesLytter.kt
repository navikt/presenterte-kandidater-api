package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.arbeidsgiver.toi.presentertekandidater.log
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.time.ZonedDateTime
import java.util.*

class HendelsesLytter(
    rapidsConnection: RapidsConnection,
    private val notifikasjonPubliserer: NotifikasjonPubliserer,
    prometheusRegistry: MeterRegistry,
    private val presenterteKandidaterService: PresenterteKandidaterService
) : River.PacketListener {

    private val cvDeltCounter: Counter = Counter.builder("cvDelt").register(prometheusRegistry)

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAny(
                    "@event_name", listOf(
                        "kandidat_v2.DelCvMedArbeidsgiver"
                    )
                )
                it.requireKey(
                    "stilling",
                    "stillingstittel",
                    "organisasjonsnummer",
                    "tidspunkt",
                    "stillingsId",
                    "utførtAvNavIdent",
                    "utførtAvNavKontorKode",
                    "kandidater")
                it.rejectValue("@slutt_av_hendelseskjede", true)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val organisasjonsnummer = packet["organisasjonsnummer"].asText()
        val tidspunkt = packet["tidspunkt"].asZonedDateTime()
        val stillingsId = UUID.fromString(packet["stillingsId"].asText())
        val utførtAvNavIdent = packet["utførtAvNavIdent"]
        val utførtAvNavKontorKode = packet["utførtAvNavKontorKode"]
        val aktørIder = packet["kandidater"].fields().asSequence()
            .map(MutableMap.MutableEntry<String, JsonNode>::key).toList()
        val stillingstittel = packet["stillingstittel"].asText()

        cvDeltCounter.increment()

        presenterteKandidaterService.lagreCvDeltHendelse(
            organisasjonsnummer = organisasjonsnummer,
            stillingsId = stillingsId,
            stillingstittel = stillingstittel,
            aktørIder = aktørIder
            )

        val cvDeltData = hentUtCvDeltData(packet)

        if (cvDeltData != null) {
            notifikasjonPubliserer.publiserNotifikasjonForCvDelt(tidspunkt, stillingsId, organisasjonsnummer, cvDeltData)
        } else {
            packet["@slutt_av_hendelseskjede"] = true
            context.publish(packet.toJson())
        }
    }

    private fun JsonNode.asZonedDateTime() =
        ZonedDateTime.parse(this.asText())

    private fun hentUtCvDeltData(kandidathendelsePacket: JsonMessage): CvDeltData? {
        return try {
            objectMapper.readValue(kandidathendelsePacket.toJson(), CvDeltData::class.java)
        } catch (e: Exception) {
            log.error("Kunne ikke hente CvDeltData fra kandidathendelseJson", e)
            null
        }
    }
}