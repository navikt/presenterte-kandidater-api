package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.arbeidsgiver.toi.presentertekandidater.log
import no.nav.arbeidsgiver.toi.presentertekandidater.secureLog
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

class PresenterteKandidaterLytter(
    rapidsConnection: RapidsConnection,
    private val notifikasjonPubliserer: NotifikasjonPubliserer,
    prometheusRegistry: MeterRegistry,
    private val presenterteKandidaterService: PresenterteKandidaterService,

    ) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAny(
                    "@event_name", listOf(
                        "kandidat.annullert",
                        "kandidat.slettet-fra-arbeidsgivers-kandidatliste",
                    )
                )
                it.demandKey("kandidathendelse")
                it.demandKey("stilling")
                it.rejectValue("@slutt_av_hendelseskjede", true)
            }
        }.register(this)

    }

    private val cvSlettetCounter: Counter = Counter.builder("cvSlettet").register(prometheusRegistry)

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        try {
            val kandidathendelsePacket = packet["kandidathendelse"]
            val kandidathendelse = objectMapper.treeToValue(kandidathendelsePacket, Kandidathendelse::class.java)

            log.info("Mottok event ${kandidathendelse.type}. Se SecureLog for aktørId")
            secureLog.info("Mottok event ${kandidathendelse.type} for aktørid ${kandidathendelse.aktørId}")

            val harPublisertNyMeldingPåRapid = when (kandidathendelse.type) {

                Type.SLETTET_FRA_ARBEIDSGIVERS_KANDIDATLISTE -> {
                    presenterteKandidaterService.slettKandidatFraKandidatliste(
                        kandidathendelse.aktørId,
                        kandidathendelse.stillingsId
                    )
                    cvSlettetCounter.increment()
                    false
                }
            }

            if (!harPublisertNyMeldingPåRapid) {
                packet["@slutt_av_hendelseskjede"] = true
                context.publish(packet.toJson())
            }
        } catch (e: Exception) {
            log.error(
                "Feil ved mottak av kandidathendelse. Dette må håndteres: ${e.message}.",
                e
            )
            throw e
        }
    }

    private fun hentUtCvDeltData(kandidathendelsePacket: JsonNode): CvDeltData? {
        return try {
            objectMapper.treeToValue(kandidathendelsePacket, CvDeltData::class.java)
        } catch (e: Exception) {
            log.error("Kunne ikke hente CvDeltData fra kandidathendelseJson", e)
            null
        }
    }
}
