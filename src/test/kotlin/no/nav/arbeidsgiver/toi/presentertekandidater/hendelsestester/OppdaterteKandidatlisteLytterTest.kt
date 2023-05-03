package no.nav.arbeidsgiver.toi.presentertekandidater.hendelsestester

import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatlisteRepositoryMedLokalPostgres
import no.nav.arbeidsgiver.toi.presentertekandidater.startLocalApplication
import no.nav.arbeidsgiver.toi.presentertekandidater.testRapid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OppdaterteKandidatlisteLytterTest {

    private val repository = kandidatlisteRepositoryMedLokalPostgres()

    @BeforeAll
    fun init() {
        startLocalApplication()
    }

    @BeforeEach
    fun setUp() {
        testRapid.reset()
    }

    @Test
    fun `Skal lagre kandidatliste når vi mottar OppdaterteKandidatliste-melding med stillingsdata`() {
        val stillingsId = UUID.randomUUID()
        val stillingstittel = "En stilling"
        val virksomhetsnummer = "312113341"
        val melding = melding(stillingsId, stillingstittel, virksomhetsnummer)
        
        testRapid.sendTestMessage(melding)

        val kandidatliste = repository.hentKandidatliste(stillingsId)
        assertNotNull(kandidatliste)
        assertNotNull(kandidatliste.uuid)
        assertThat(kandidatliste.stillingId).isEqualTo(stillingsId)
        assertThat(kandidatliste.tittel).isEqualTo(stillingstittel)
        assertThat(kandidatliste.status).isEqualTo(Kandidatliste.Status.ÅPEN)
        assertThat(kandidatliste.slettet).isFalse
        assertThat(kandidatliste.virksomhetsnummer).isEqualTo(virksomhetsnummer)
        assertNotNull(kandidatliste.id)
        val kandidater = repository.hentKandidater(kandidatliste.id!!)
        assertThat(kandidater).hasSize(0)
    }

    @Test
    fun `Behandling av meldinger skal være idempotent`() {}

    // Særlig aktuelt for status og stillingstittel
    @Test
    fun `Når vi mottar melding om kandidatliste vi allerede har lagret skal vi oppdatere hvis opplysninger er endret`() {}

    @Test
    fun `Vi skal ikke behandle melding uten stillingsdata`() {}

    @Test
    fun `Ved mottak av slutt_av_hendelseskjede satt til true skal det ikke legges ut ny hendelse på rapid`() {}

    private fun melding(
        stillingsId: UUID,
        stillingstittel: String = "En stilling",
        virksomhetsnummer: String = "312113341") = """
        {
          "antallKandidater": 0,
          "organisasjonsnummer": "312113341",
          "kandidatlisteId": "773571f9-026a-49b9-83a0-948658dd4e1c",
          "tidspunkt": "2023-05-03T14:17:26.851+02:00",
          "stillingsId": "$stillingsId",
          "utførtAvNavIdent": "Z994241",
          "@event_name": "kandidat_v2.OppdaterteKandidatliste",
          "system_participating_services": [
            {
              "id": "a4a136f4-5465-49d4-ac53-73758c03e814",
              "time": "2023-05-03T14:17:30.002826698",
              "service": "rekrutteringsbistand-kandidat-api",
              "instance": "rekrutteringsbistand-kandidat-api-8667847c9d-zrgxk",
              "image": "ghcr.io/navikt/rekrutteringsbistand-kandidat-api/rekrutteringsbistand-kandidat-api:b0cd5126f43d3599f27e31c3d945119bef7d77e1"
            },
            {
              "id": "4039476a-02a9-48e3-808b-98db780128fa",
              "time": "2023-05-03T14:17:30.025585409",
              "service": "rekrutteringsbistand-stilling-api",
              "instance": "rekrutteringsbistand-stilling-api-6b994f9798-lh5l9",
              "image": "ghcr.io/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:0070a4190c776bee3f058d11e53959cdb5703085"
            },
            {
              "id": "f8e84852-c2f6-45fe-a696-16e5d972b71a",
              "time": "2023-05-03T14:17:30.135489548",
              "service": "rekrutteringsbistand-stilling-api",
              "instance": "rekrutteringsbistand-stilling-api-6b994f9798-lh5l9",
              "image": "ghcr.io/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:0070a4190c776bee3f058d11e53959cdb5703085"
            }
          ],
          "@id": "8092ffc5-699f-4e88-80f9-6c05156bf2c7",
          "@opprettet": "2023-05-03T14:17:30.179323178",
          "system_read_count": 1,
          "stillingsinfo": {
            "stillingsinfoid": "98780f09-cb1e-4be2-adeb-e01dae01cbe2",
            "stillingsid": "$stillingsId",
            "eier": null,
            "notat": "Stopper",
            "stillingskategori": "STILLING"
          },
          "stilling": {
            "stillingstittel": "$stillingstittel",
            "erDirektemeldt": true,
            "stillingOpprettetTidspunkt": "2023-05-03T14:16:42.859069+02:00[Europe/Oslo]",
            "antallStillinger": 2,
            "organisasjonsnummer": "$virksomhetsnummer",
            "stillingensPubliseringstidspunkt": "2023-05-03T14:16:42.859069+02:00[Europe/Oslo]"
          },
          "@forårsaket_av": {
            "id": "f8e84852-c2f6-45fe-a696-16e5d972b71a",
            "opprettet": "2023-05-03T14:17:30.135489548",
            "event_name": "kandidat_v2.OppdaterteKandidatliste"
          },
          "@slutt_av_hendelseskjede": false
        }
    """.trimIndent()

    private fun lagGyldigKandidatliste(stillingsId: UUID): Kandidatliste = Kandidatliste(
        id = null,
        uuid = UUID.randomUUID(),
        stillingsId, "Tittel",
        Kandidatliste.Status.ÅPEN,
        false,
        "",
        ZonedDateTime.now(),
        ZonedDateTime.now()
    )
}