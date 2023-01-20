package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser


import no.nav.helse.rapids_rivers.RapidsConnection

class NotifikasjonPubliserer(val rapidsConnection: RapidsConnection) {

    fun publiserNotifikasjonForCvDelt(kandidathendelse: Kandidathendelse, cvDeltData: CvDeltData) {
        val notifikasjonsId = "${kandidathendelse.stillingsId}-${kandidathendelse.tidspunkt}"
        val melding = """
            {
                "@event_name": "notifikasjon.cv-delt",
                "notifikasjonsId": "$notifikasjonsId",
                "stillingsId": "${kandidathendelse.stillingsId}",
                "virksomhetsnummer": "${kandidathendelse.organisasjonsnummer}",
                "utførendeVeilederFornavn": "${cvDeltData.utførendeVeilederFornavn}",
                "utførendeVeilederEtternavn": "${cvDeltData.utførendeVeilederEtternavn}",
                "mottakerEpost": "${cvDeltData.mottakerEpost}"
            }
        """.trimIndent()
        rapidsConnection.publish(notifikasjonsId, melding)
    }
}
