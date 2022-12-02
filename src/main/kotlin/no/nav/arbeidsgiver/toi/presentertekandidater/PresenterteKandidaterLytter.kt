package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

class PresenterteKandidaterLytter(
    rapidsConnection: RapidsConnection,
    private val presenterteKandidaterService: PresenterteKandidaterService
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
                    it.interestedIn("finnesJoIkke")
            }
        }.register(this)
    }

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
//        try {
            val finnesIkkePacket = packet["finnesJoIkke"].asText().toInt()
            val kandidathendelsePacket = packet["kandidathendelse"]
            val kandidathendelse = objectMapper.treeToValue(kandidathendelsePacket, Kandidathendelse::class.java)

            log.info("Mottok event ${kandidathendelse.type} for aktørid ${kandidathendelse.aktørId}")

            when (kandidathendelse.type) {
                Type.CV_DELT_VIA_REKRUTTERINGSBISTAND -> {
                    val stillingstittel = packet["stilling"]["stillingstittel"].asText()
                    presenterteKandidaterService.lagreKandidathendelse(kandidathendelse, stillingstittel)
                }

                Type.SLETTET_FRA_ARBEIDSGIVERS_KANDIDATLISTE ->
                    presenterteKandidaterService.slettKandidatFraKandidatliste(
                        kandidathendelse.aktørId,
                        kandidathendelse.stillingsId
                    )

                Type.ANNULLERT ->
                    presenterteKandidaterService.markerKandidatlisteSomSlettet(kandidathendelse.stillingsId)

                Type.KANDIDATLISTE_LUKKET_NOEN_ANDRE_FIKK_JOBBEN, Type.KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN ->
                    presenterteKandidaterService.lukkKandidatliste(kandidathendelse.stillingsId)
            }
//        } catch (e: Exception) {
//            log.error("Feil ved mottak av kandidathendelse.", e)
//        }
    }
}
