package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser

import no.nav.helse.rapids_rivers.*

class OpprettetKandidatlisteLytter(
    rapidsConnection: RapidsConnection,
    private val presenterteKandidaterService: PresenterteKandidaterService
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "kandidat_v2.OpprettetKandidatliste")
                it.demandKey("stillingsId")
                it.demandKey("stilling.stillingstittel")
                it.demandKey("stilling.organisasjonsnummer")
                it.rejectValue("@slutt_av_hendelseskjede", true)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        presenterteKandidaterService.lagreOpprettetKandidatlisteHendelse(
            stillingsId = packet["stillingsId"].asText().toUUID(),
            stillingstittel = packet["stilling.stillingstittel"].asText(),
            virksomhetsnummer = packet["stilling.organisasjonsnummer"].asText()
        )
        packet["@slutt_av_hendelseskjede"] = true
        context.publish(packet.toJson())
    }
}
