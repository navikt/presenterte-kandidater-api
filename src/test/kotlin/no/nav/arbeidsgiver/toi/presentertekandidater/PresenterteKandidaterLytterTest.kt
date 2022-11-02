package no.nav.arbeidsgiver.toi.presentertekandidater

import io.javalin.Javalin
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PresenterteKandidaterLytterTest {

    private lateinit var javalin: Javalin
    private lateinit var testRapid: TestRapid
    private lateinit var presenterteKandidaterService: PresenterteKandidaterService

    @BeforeAll
    fun init() {
        testRapid = TestRapid()
        val repository = opprettTestRepositoryMedLokalPostgres()
        presenterteKandidaterService = PresenterteKandidaterService(repository)

        startLocalApplication(rapid = testRapid, presenterteKandidaterService = presenterteKandidaterService)
    }

    @Disabled
    @Test
    fun `tester melding på rapid`() {
        val kandidathendelseMelding = kandidathendelseMelding()
        PresenterteKandidaterLytter(testRapid, presenterteKandidaterService)
        testRapid.sendTestMessage(kandidathendelseMelding)
    }

    private fun kandidathendelseMelding() =
        """
            {
              "aktørId": "123",
              "@event_name": "kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand",
              "kandidathendelse": {
                "type": "CV_DELT_VIA_REKRUTTERINGSBISTAND",
                "aktørId": "dummyAktørid",
                "organisasjonsnummer": "123456789",
                "kandidatlisteId": "24e81692-37ef-4fda-9b55-e17588f65061",
                "tidspunkt": "2022-09-18T10:33:02.5+02:00",
                "stillingsId": "b3c925af-ebf4-50d1-aeee-efc9259107a4",
                "utførtAvNavIdent": "Z994632",
                "utførtAvNavKontorKode": "0313",
                "synligKandidat": true,
                "harHullICv": true,
                "alder": 62,
                "tilretteleggingsbehov": [
                  "arbeidstid"
                ]
              },
              "stillingsinfo": {
                "stillingsinfoid": "5c139f31-6526-4161-a804-585d6c0a8619",
                "stillingsid": "b3c925af-ebf4-50d1-aeee-efc9259107a4",
                "eier": {
                  "navident": "A123456",
                  "navn": "Navnesen"
                },
                "stillingskategori": "STILLING"
              },
              "stillingstittel": "En stillingstittel"
            }
        """.trimIndent()
}