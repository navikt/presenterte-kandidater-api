package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.arbeidsgiver.toi.presentertekandidater.log
import no.nav.helse.rapids_rivers.*

class SlettetStillingLytter(
    rapidsConnection: RapidsConnection,
    prometheusRegistry: PrometheusMeterRegistry,
    private val presenterteKandidaterService: PresenterteKandidaterService
) : River.PacketListener {
    private val annullertCounter: Counter = Counter.builder("cvAnnullert").register(prometheusRegistry)

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "kandidat_v2.SlettetStillingOgKandidatliste")
                it.requireKey("stillingsId")
                it.rejectValue("@slutt_av_hendelseskjede", true)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val stillingsId = packet["stillingsId"].asText()
        presenterteKandidaterService.markerKandidatlisteSomSlettet(stillingsId.toUUID())
        annullertCounter.increment()
        packet["@slutt_av_hendelseskjede"] = true
        context.publish(packet.toJson())
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error("feil ved lesing av hendelse: $problems")
    }
}