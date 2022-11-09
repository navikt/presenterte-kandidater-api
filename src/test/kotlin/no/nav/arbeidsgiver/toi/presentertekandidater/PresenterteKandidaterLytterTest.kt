package no.nav.arbeidsgiver.toi.presentertekandidater

import io.javalin.Javalin
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PresenterteKandidaterLytterTest {

    private lateinit var javalin: Javalin
    private lateinit var testRapid: TestRapid
    private lateinit var presenterteKandidaterService: PresenterteKandidaterService
    private lateinit var repository: Repository

    @BeforeAll
    fun init() {
        testRapid = TestRapid()
        repository = opprettTestRepositoryMedLokalPostgres()
        presenterteKandidaterService = PresenterteKandidaterService(repository)
        startLocalApplication(rapid = testRapid, presenterteKandidaterService = presenterteKandidaterService)
    }

    @Test
    fun `når vi får en melding om kandidathendelse så skal kandidatlisten og kandidaten lagres med riktige verdier `() {
        val kandidathendelseMelding = kandidathendelseMelding()
        PresenterteKandidaterLytter(testRapid, presenterteKandidaterService)
        testRapid.sendTestMessage(kandidathendelseMelding)
        val kandidatliste = repository.hentKandidatliste(UUID.fromString("fa85076a-504a-4396-a55f-2f414e1d5a16"))

        // Verifiser kandidatliste
        assertNotNull(kandidatliste)
        assertThat(kandidatliste.stillingId).isEqualTo(UUID.fromString("fa85076a-504a-4396-a55f-2f414e1d5a16"))
        assertThat(kandidatliste.tittel).isEqualTo("Noen skal få denne jobben!")
        assertThat(kandidatliste.status).isEqualTo(Kandidatliste.Status.ÅPEN)
        assertThat(kandidatliste.slettet).isFalse
        assertThat(kandidatliste.virksomhetsnummer).isEqualTo("912998827")
        assertNotNull(kandidatliste.id)

        // Verifiser kandidat

    }

    private fun kandidathendelseMelding() =
        """
            {
              "@event_name": "kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand",
              "kandidathendelse": {
                "type": "CV_DELT_VIA_REKRUTTERINGSBISTAND",
                "aktørId": "2040897398605",
                "organisasjonsnummer": "912998827",
                "kandidatlisteId": "08d56a3e-e1e2-4dfb-8078-363fe6489ea9",
                "tidspunkt": "2022-11-09T10:37:45.108+01:00",
                "stillingsId": "fa85076a-504a-4396-a55f-2f414e1d5a16",
                "utførtAvNavIdent": "Z994633",
                "utførtAvNavKontorKode": "0313",
                "synligKandidat": true,
                "harHullICv": true,
                "alder": 27,
                "tilretteleggingsbehov": []
              },
              "@id": "60bfc604-64ef-48b1-be1f-45ba5486a888",
              "@opprettet": "2022-11-09T10:38:02.181523695",
              "system_read_count": 0,
              "system_participating_services": [
                {
                  "id": "7becad81-fe66-4800-8bbe-abce2e4dbf75",
                  "time": "2022-11-09T10:38:00.057867691",
                  "service": "rekrutteringsbistand-stilling-api",
                  "instance": "rekrutteringsbistand-stilling-api-544d69cf7b-cpvgs",
                  "image": "docker.pkg.github.com/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:88c48704fc1a9db29f744f7e9f6c7bad6c390e5b"
                },
                {
                  "id": "60bfc604-64ef-48b1-be1f-45ba5486a888",
                  "time": "2022-11-09T10:38:02.181523695",
                  "service": "rekrutteringsbistand-stilling-api",
                  "instance": "rekrutteringsbistand-stilling-api-544d69cf7b-cpvgs",
                  "image": "docker.pkg.github.com/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:88c48704fc1a9db29f744f7e9f6c7bad6c390e5b"
                }
              ],
              "stillingsinfo": {
                "stillingsinfoid": "ba9b1395-c7b5-4cdc-8060-d5b92ecde52e",
                "stillingsid": "fa85076a-504a-4396-a55f-2f414e1d5a16",
                "eier": null,
                "notat": null,
                "stillingskategori": "STILLING"
              },
              "stilling": {
                "stillingstittel": "Noen skal få denne jobben!"
              },
              "@forårsaket_av": {
                "id": "7becad81-fe66-4800-8bbe-abce2e4dbf75",
                "opprettet": "2022-11-09T10:38:00.057867691",
                "event_name": "kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand"
              }
            }
        """.trimIndent()
}
