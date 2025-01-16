package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry

class OpprettetKandidatlisteLytter(
    rapidsConnection: RapidsConnection,
    private val presenterteKandidaterService: PresenterteKandidaterService
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "kandidat_v2.OpprettetKandidatliste")
                it.requireKey("stillingsId")
                it.requireKey("stilling.stillingstittel")
                it.requireKey("stilling.organisasjonsnummer")
                it.requireValue("stillingsinfo.stillingskategori", "STILLING")
                it.forbidValue("@slutt_av_hendelseskjede", true)
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
        presenterteKandidaterService.lagreOpprettetKandidatlisteHendelse(
            stillingsId = packet["stillingsId"].asText().toUUID(),
            stillingstittel = packet["stilling.stillingstittel"].asText(),
            virksomhetsnummer = packet["stilling.organisasjonsnummer"].asText()
        )
        packet["@slutt_av_hendelseskjede"] = true
        context.publish(packet.toJson())
    }
}
