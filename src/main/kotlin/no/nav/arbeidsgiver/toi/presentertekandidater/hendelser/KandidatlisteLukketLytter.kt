package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.arbeidsgiver.toi.presentertekandidater.log
import no.nav.helse.rapids_rivers.*

class KandidatlisteLukketLytter(
    rapidsConnection: RapidsConnection,
    prometheusRegistry: MeterRegistry,
    private val presenterteKandidaterService: PresenterteKandidaterService
) : River.PacketListener {
    private val kandidatlisteLukketCounter: Counter =
        Counter.builder("kandidatlisteLukket").register(prometheusRegistry)

    init {
        River(rapidsConnection).apply {
            precondition{
                it.requireValue("@event_name", "kandidat_v2.LukketKandidatliste")
                it.forbidValue("@slutt_av_hendelseskjede", true)
            }
            validate {
                it.requireKey("stillingsId")
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
    val stillingsId = packet["stillingsId"].asText()
        presenterteKandidaterService.lukkKandidatliste(stillingsId.toUUID())
        kandidatlisteLukketCounter.increment()
        packet["@slutt_av_hendelseskjede"] = true
        context.publish(packet.toJson())
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        log.error("feil ved lesing av hendelse: $problems")
        super.onError(problems, context, metadata)
    }
}