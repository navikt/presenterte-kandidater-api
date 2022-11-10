package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

class PresenterteKandidaterLytter(rapidsConnection: RapidsConnection, private val presenterteKandidaterService: PresenterteKandidaterService) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand")
                it.demandKey("kandidathendelse")
                it.demandKey("stilling")
            }
        }.register(this)
    }

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val kandidathendelsePacket = packet["kandidathendelse"]
        val eventtype = kandidathendelsePacket["type"]
        val stillingstittel = packet["stilling"]["stillingstittel"].asText()
        val kandidathendelse = objectMapper.treeToValue(kandidathendelsePacket, Kandidathendelse::class.java)
        log.info("Mottok event $eventtype for aktørid ${kandidathendelse.aktørId}")
        presenterteKandidaterService.lagreKandidathendelse(kandidathendelse, stillingstittel)
    }
}

