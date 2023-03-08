package no.nav.arbeidsgiver.toi.presentertekandidater.visningkontaktinfo

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.arbeidsgiver.toi.presentertekandidater.log
import no.nav.helse.rapids_rivers.RapidsConnection
import java.time.ZonedDateTime
import java.util.*

class VisningKontaktinfoPubliserer(
    private val rapidsConnection: RapidsConnection,
    private val visningKontaktinfoRepository: VisningKontaktinfoRepository
) {

   init {
        val antallMillisekunderIMinutt = 60000L
        val tidTilFørsteKjøringMillisekunder = antallMillisekunderIMinutt
        val tidMellomHverKjøringMillisekunder = antallMillisekunderIMinutt

        val jobb = object : TimerTask() {
            override fun run() {
                publiser()
            }
        }

        Timer().scheduleAtFixedRate(jobb,
            tidTilFørsteKjøringMillisekunder,
            tidMellomHverKjøringMillisekunder
        )
    }

    fun publiser() {
        log.info("Skal publisere meldinger for visning av kontaktinfo")
        var antallMeldingerPublisert = 0

        visningKontaktinfoRepository.gjørOperasjonPåAlleUpubliserteVisninger { registrertVisning, index ->
            val melding = Melding(
                stillingsId = registrertVisning.stillingsId,
                tidspunkt = registrertVisning.tidspunkt,
                aktørId = registrertVisning.aktørId
            )

            rapidsConnection.publish(melding.tilJsonMelding())
            visningKontaktinfoRepository.markerSomPublisert(registrertVisning)
            antallMeldingerPublisert++
        }

        log.info("Har publisert $antallMeldingerPublisert meldinger om vist kontaktinfo")
    }

    private data class Melding(
        val stillingsId: UUID,
        val tidspunkt: ZonedDateTime,
        val aktørId: String
    ) {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        fun tilJsonMelding() = objectMapper.valueToTree<ObjectNode>(this).apply {
            put("@event_name", "arbeidsgiversKandidatliste.VisningKontaktinfo")
        }.toString()
    }
}