package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser

import no.nav.helse.rapids_rivers.*

class OppdaterteKandidatlisteLytter(
    rapidsConnection: RapidsConnection,
    private val presenterteKandidaterService: PresenterteKandidaterService
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "kandidat_v2.OppdaterteKandidatliste")
                it.demandKey("stillingsId")
                it.demandKey("stilling.stillingstittel")
                it.demandKey("stilling.organisasjonsnummer")
                it.rejectValue("@slutt_av_hendelseskjede", true)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        presenterteKandidaterService.lagreOppdatertKandidatlisteMelding(
            stillingsId = packet["stillingsId"].asText().toUUID(),
            stillingstittel = packet["stilling.stillingstittel"].asText(),
            virksomhetsnummer = packet["stilling.organisasjonsnummer"].asText()
        )
    }
}

/*
// Definisjon av kandidatliste i appen:

    @JsonIgnore
    val id: BigInteger? = null,
    val uuid: UUID,
    val stillingId: UUID,
    val tittel: String,
    val status: Status,
    val slettet: Boolean = false,
    val virksomhetsnummer: String,
    val sistEndret: ZonedDateTime,
    val opprettet: ZonedDateTime,
 */