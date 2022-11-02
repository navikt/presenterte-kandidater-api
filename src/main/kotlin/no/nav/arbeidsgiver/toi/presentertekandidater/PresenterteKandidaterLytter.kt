package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

class PresenterteKandidaterLytter(private val rapidsConnection: RapidsConnection, private val presenterteKandidaterService: PresenterteKandidaterService) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandKey("aktørId")
                it.demandValue("@event_name", "kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand")
                it.demandKey("kandidathendelse")
                it.demandKey("stillingstittel")
            }
        }.register(this)
    }

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val aktørId = packet["aktørId"].asText()
        val kandidathendelsePacket = packet["kandidathendelse"]
        val eventtype = kandidathendelsePacket["type"]
        log.info("Mottok event $eventtype for aktørid $aktørId")
        val stillingstittel = packet["stillingstittel"].asText()
        val kandidathendelse = objectMapper.treeToValue(kandidathendelsePacket, Kandidathendelse::class.java)
        presenterteKandidaterService.lagreKandidathendelse(kandidathendelse, stillingstittel)
    }

}

