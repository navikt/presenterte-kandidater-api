package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.arbeidsgiver.toi.presentertekandidater.log
import no.nav.helse.rapids_rivers.*

class SlettFraArbeidsgiversKandidatlisteLytter(
    rapidsConnection: RapidsConnection,
    prometheusRegistry: MeterRegistry,
    private val presenterteKandidaterService: PresenterteKandidaterService
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "kandidat_v2.SlettFraArbeidsgiversKandidatliste")
                it.requireKey("stillingsId")
                it.requireKey("aktørId")
                it.rejectValue("@slutt_av_hendelseskjede", true)
            }
        }.register(this)
    }

    private val cvSlettetCounter: Counter = Counter.builder("cvSlettet").register(prometheusRegistry)

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val stillingsId = packet["stillingsId"].asText().toUUID()
        val aktørId = packet["aktørId"].asText()

        log.info("Mottatt hendelse om å slette kandidat fra kandidatliste til stilling: $stillingsId")

        presenterteKandidaterService.slettKandidatFraKandidatliste(aktørId, stillingsId)
        cvSlettetCounter.increment()

        packet["@slutt_av_hendelseskjede"] = true
        context.publish(packet.toJson())
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error("feil ved lesing av hendelse: $problems")
    }
}