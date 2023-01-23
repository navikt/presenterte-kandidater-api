package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser


import no.nav.helse.rapids_rivers.RapidsConnection
import java.time.ZoneId

class NotifikasjonPubliserer(val rapidsConnection: RapidsConnection) {

    fun publiserNotifikasjonForCvDelt(kandidathendelse: Kandidathendelse, cvDeltData: CvDeltData) {
        val notifikasjonsId = "${kandidathendelse.stillingsId}-${kandidathendelse.tidspunkt.withZoneSameInstant(ZoneId.of("Europe/Oslo"))}"
        val melding = """
            {
                "@event_name": "notifikasjon.cv-delt",
                "notifikasjonsId": "$notifikasjonsId",
                "stillingsId": "${kandidathendelse.stillingsId}",
                "virksomhetsnummer": "${kandidathendelse.organisasjonsnummer}",
                "utførtAvVeilederFornavn": "${cvDeltData.utførtAvVeilederFornavn}",
                "utførtAvVeilederEtternavn": "${cvDeltData.utførtAvVeilederEtternavn}",
                "epostAdresseArbeidsgiver": "${cvDeltData.epostAdresseArbeidsgiver}"
            }
        """.trimIndent()
        rapidsConnection.publish(kandidathendelse.stillingsId.toString(), melding)
    }
}
