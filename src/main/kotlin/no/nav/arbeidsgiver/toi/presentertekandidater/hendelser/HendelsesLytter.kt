package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser

import com.fasterxml.jackson.databind.JsonNode
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

class HendelsesLytter(
    rapidsConnection: RapidsConnection,
    private val notifikasjonPubliserer: NotifikasjonPubliserer,
    prometheusRegistry: MeterRegistry,
    private val presenterteKandidaterService: PresenterteKandidaterService
) : River.PacketListener {
    private val cvDeltCounter: Counter = Counter.builder("cvDelt").register(prometheusRegistry)

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAny(
                    "@event_name", listOf(
                        "kandidat_v2.DelCvMedArbeidsgiver"
                    )
                )
                it.demandKey("stilling")
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

        presenterteKandidaterService.lagreNyCvDeltHendelse(
            organisasjonsnummer = organisasjonsnummer,
            stillingsId = stillingsId,
            stillingstittel = stillingstittel,
            aktørIder = aktørIder
            )

        // notifikasjonPubliserer.publiserNotifikasjonForCvDelt(kandidathendelse, cvDeltData)
    }

    private fun JsonNode.asZonedDateTime() =
        ZonedDateTime.parse(this.asText()).withZoneSameInstant(ZoneId.of("Europe/Oslo"))
}