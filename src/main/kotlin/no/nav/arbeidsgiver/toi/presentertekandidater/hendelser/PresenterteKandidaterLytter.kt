package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.arbeidsgiver.toi.presentertekandidater.log
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

class PresenterteKandidaterLytter(
    rapidsConnection: RapidsConnection,
    private val prometheusRegistry: MeterRegistry,
    private val presenterteKandidaterService: PresenterteKandidaterService,

    ) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAny(
                    "@event_name", listOf(
                        "kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand",
                        "kandidat.annullert",
                        "kandidat.slettet-fra-arbeidsgivers-kandidatliste",
                        "kandidat.kandidatliste-lukket-noen-andre-fikk-jobben",
                        "kandidat.kandidatliste-lukket-ingen-fikk-jobben"
                    )
                )
                it.demandKey("kandidathendelse")
                it.demandKey("stilling")
                it.rejectValue("@slutt_av_hendelseskjede", true)
            }
        }.register(this)

    }

    private val cvDeltCounter: Counter = Counter.builder("cvDelt").register(prometheusRegistry)
    private val cvSlettetCounter: Counter = Counter.builder("cvSlettet").register(prometheusRegistry)
    private val annullertCounter: Counter = Counter.builder("cvAnnullert").register(prometheusRegistry)
    private val kandidatlisteLukketCounter: Counter =
        Counter.builder("kandidatlisteLukket").register(prometheusRegistry)

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        try {
            val kandidathendelsePacket = packet["kandidathendelse"]
            val kandidathendelse = objectMapper.treeToValue(kandidathendelsePacket, Kandidathendelse::class.java)

            log.info("Mottok event ${kandidathendelse.type} for akt??rid ${kandidathendelse.akt??rId}")

            when (kandidathendelse.type) {
                Type.CV_DELT_VIA_REKRUTTERINGSBISTAND -> {
                    val stillingstittel = packet["stilling"]["stillingstittel"].asText()
                    presenterteKandidaterService.lagreCvDeltHendelse(kandidathendelse, stillingstittel)
                    cvDeltCounter.increment()
                }

                Type.SLETTET_FRA_ARBEIDSGIVERS_KANDIDATLISTE -> {
                    presenterteKandidaterService.slettKandidatFraKandidatliste(
                        kandidathendelse.akt??rId,
                        kandidathendelse.stillingsId
                    )
                    cvSlettetCounter.increment()
                }

                Type.ANNULLERT -> {
                    presenterteKandidaterService.markerKandidatlisteSomSlettet(kandidathendelse.stillingsId)
                    annullertCounter.increment()
                }

                Type.KANDIDATLISTE_LUKKET_NOEN_ANDRE_FIKK_JOBBEN, Type.KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN -> {
                    presenterteKandidaterService.lukkKandidatliste(kandidathendelse.stillingsId)
                    kandidatlisteLukketCounter.increment()
                }
            }
            packet["@slutt_av_hendelseskjede"] = true
            context.publish(packet.toJson())
        } catch (e: Exception) {
            log.error(
                "Feil ved mottak av kandidathendelse. Dette m?? h??ndteres: ${e.message}.",
                e
            )
            throw e
        }
    }
}
