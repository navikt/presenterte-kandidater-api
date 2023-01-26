package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser


import no.nav.helse.rapids_rivers.RapidsConnection
import java.time.ZoneId

class NotifikasjonPubliserer(val rapidsConnection: RapidsConnection) {

    fun publiserNotifikasjonForCvDelt(kandidathendelse: Kandidathendelse, cvDeltData: CvDeltData) {
        val tidspunkt = kandidathendelse.tidspunkt.withZoneSameInstant(ZoneId.of("Europe/Oslo"))
        val notifikasjonsId = "${kandidathendelse.stillingsId}-$tidspunkt"

        val melding = """
            {
                "@event_name": "notifikasjon.cv-delt",
                "notifikasjonsId": "$notifikasjonsId",
                "stillingsId": "${kandidathendelse.stillingsId}",
                "tidspunktForHendelse": "$tidspunkt",
                "virksomhetsnummer": "${kandidathendelse.organisasjonsnummer}",
                "utførtAvVeilederFornavn": "${cvDeltData.utførtAvVeilederFornavn}",
                "utførtAvVeilederEtternavn": "${cvDeltData.utførtAvVeilederEtternavn}",
                "arbeidsgiversEpostadresser": ${cvDeltData.arbeidsgiversEpostadresser.map { "\"$it\"" }},
                "meldingTilArbeidsgiver": "${cvDeltData.meldingTilArbeidsgiver}",
                "stillingstittel": "${cvDeltData.stillingstittel}"
            }
        """.trimIndent()
        rapidsConnection.publish(kandidathendelse.stillingsId.toString(), melding)
    }
}
