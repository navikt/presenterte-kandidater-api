package no.nav.arbeidsgiver.toi.presentertekandidater

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*
import java.util.*
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PresenterteKandidaterLytterTest {
    private val javalin = opprettJavalinMedTilgangskontroll(issuerProperties)
    private val testRapid = TestRapid()
    private val repository = opprettTestRepositoryMedLokalPostgres()
    private val presenterteKandidaterService = PresenterteKandidaterService(repository)

    @BeforeAll
    fun init() {
        startLocalApplication(
            rapid = testRapid,
            presenterteKandidaterService = presenterteKandidaterService,
            javalin = javalin
        )
    }

    @AfterAll
    fun cleanUp() {
        javalin.stop()
    }

    @Test
    fun `Skal lagre kandidatliste og kandidat når vi får melding om kandidathendelse`() {
        val aktørId = "2040897398605"
        val stillingsId = UUID.randomUUID()
        val melding = meldingOmKandidathendelseDeltCv(aktørId = aktørId, stillingsId = stillingsId)

        testRapid.sendTestMessage(melding)

        PresenterteKandidaterLytter(testRapid, presenterteKandidaterService)
        val kandidatliste =
            repository.hentKandidatliste(stillingsId)
        val kandidater = repository.hentKandidater(kandidatliste?.id!!)

        // Verifiser kandidatliste
        assertNotNull(kandidatliste)
        assertThat(kandidatliste.uuid)
        assertThat(kandidatliste.stillingId).isEqualTo(stillingsId)
        assertThat(kandidatliste.tittel).isEqualTo("Noen skal få denne jobben!")
        assertThat(kandidatliste.status).isEqualTo(Kandidatliste.Status.ÅPEN)
        assertThat(kandidatliste.slettet).isFalse
        assertThat(kandidatliste.virksomhetsnummer).isEqualTo("912998827")
        assertNotNull(kandidatliste.id)

        // Verifiser kandidat
        assertThat(kandidater).hasSize(1)
        val kandidat = kandidater.first()
        assertNotNull(kandidat.id)
        assertThat(kandidat.aktørId).isEqualTo(aktørId)
        assertThat(kandidat.kandidatlisteId).isEqualTo(kandidatliste.id)
        assertNotNull(kandidat.uuid)
    }

    @Test
    fun `Konsumering av melding skal være idempotent`() {
        val aktørId = "2040897398605"
        val stillingsId = UUID.randomUUID()
        val melding = meldingOmKandidathendelseDeltCv(aktørId = aktørId, stillingsId = stillingsId)

        testRapid.sendTestMessage(melding)
        testRapid.sendTestMessage(melding)

        PresenterteKandidaterLytter(testRapid, presenterteKandidaterService)
        val kandidatliste = repository.hentKandidatliste(stillingsId)
        val kandidater = repository.hentKandidater(kandidatliste?.id!!)

        // Verifiser kandidatliste
        assertNotNull(kandidatliste)
        assertThat(kandidatliste.uuid)
        assertThat(kandidatliste.stillingId).isEqualTo(stillingsId)
        assertThat(kandidatliste.tittel).isEqualTo("Noen skal få denne jobben!")
        assertThat(kandidatliste.status).isEqualTo(Kandidatliste.Status.ÅPEN)
        assertThat(kandidatliste.slettet).isFalse
        assertThat(kandidatliste.virksomhetsnummer).isEqualTo("912998827")
        assertNotNull(kandidatliste.id)

        // Verifiser kandidat
        assertThat(kandidater).hasSize(1)
        val kandidat = kandidater.first()
        assertNotNull(kandidat.id)
        assertThat(kandidat.aktørId).isEqualTo(aktørId)
        assertThat(kandidat.kandidatlisteId).isEqualTo(kandidatliste.id)
        assertNotNull(kandidat.uuid)
    }

    @Test
    fun `Skal lagre begge kandidater når vi får meldinger om kandidathendelser som gjelder samme kandidatliste`() {
        PresenterteKandidaterLytter(testRapid, presenterteKandidaterService)
        val stillingsId = UUID.randomUUID()
        val (aktørId1, aktørId2) = listOf("1234", "5678")
        val førsteMelding = meldingOmKandidathendelseDeltCv(aktørId = aktørId1, stillingsId = stillingsId)
        val andreMelding = meldingOmKandidathendelseDeltCv(aktørId = aktørId2, stillingsId = stillingsId)

        testRapid.sendTestMessage(førsteMelding)
        testRapid.sendTestMessage(andreMelding)

        val kandidatliste = repository.hentKandidatliste(stillingsId)
        val kandidater = repository.hentKandidater(kandidatliste!!.id!!)
        assertThat(kandidater).hasSize(2)
        val (kandidat1, kandidat2) = kandidater

        assertNotNull(kandidat1.id)
        assertThat(kandidat1.aktørId).isEqualTo(aktørId1)
        assertThat(kandidat1.kandidatlisteId).isEqualTo(kandidatliste.id)
        assertNotNull(kandidat1.uuid)

        assertNotNull(kandidat2.id)
        assertThat(kandidat2.aktørId).isEqualTo(aktørId2)
        assertThat(kandidat2.kandidatlisteId).isEqualTo(kandidatliste.id)
        assertNotNull(kandidat2.uuid)

        assertThat(kandidat1.uuid).isNotEqualTo(kandidat2.uuid)
    }

    @Test
    fun `Når vi mottar kandidathendelse om en kandidatliste vi allerede har lagret, men med endrede opplysninger, skal den oppdateres`() {
        PresenterteKandidaterLytter(testRapid, presenterteKandidaterService)
        val stillingsId = UUID.randomUUID()

        val førsteAktørId = "2040897398605"
        val førsteStillingstittel = "Klovn søkes"
        val førsteKandidathendelsesmelding = meldingOmKandidathendelseDeltCv(førsteAktørId, førsteStillingstittel, stillingsId)

        testRapid.sendTestMessage(førsteKandidathendelsesmelding)
        val kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste!!.tittel).isEqualTo(førsteStillingstittel)

        val andreAktørId = "2040897398605"
        val andreStillingstittel = "Hoffnarr søkes"
        val andreKandidathendelsesmelding = meldingOmKandidathendelseDeltCv(andreAktørId, andreStillingstittel, stillingsId)

        testRapid.sendTestMessage(andreKandidathendelsesmelding)
        val oppdatertKandidatliste =
            repository.hentKandidatliste(stillingsId)
        assertThat(oppdatertKandidatliste!!.tittel).isEqualTo(andreStillingstittel)
    }

    @Test
    fun `test at lukket kandidatliste når ingen har fått jobben registreres med status LUKKET`() {
        PresenterteKandidaterLytter(testRapid, presenterteKandidaterService)
        val stillingsId = UUID.randomUUID()
        val førsteAktørId = "1122334455"
        val førsteStillingstittel = "Stilling hvis kandidatliste ble lukket!"
        val meldingOmOpprettelseAvKandidatliste = meldingOmKandidathendelseDeltCv(førsteAktørId, førsteStillingstittel, stillingsId)

        testRapid.sendTestMessage(meldingOmOpprettelseAvKandidatliste)
        var kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste!!.tittel).isEqualTo(førsteStillingstittel)
        assertThat(kandidatliste.status).isEqualTo(Kandidatliste.Status.ÅPEN)

        val meldingOmKandidatlisteLukket = meldingOmKandidathendelseKandidatlisteLukketIngenFikkJobben(førsteAktørId, førsteStillingstittel, stillingsId)
        testRapid.sendTestMessage(meldingOmKandidatlisteLukket)

        kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste!!.status).isEqualTo(Kandidatliste.Status.LUKKET)
    }

    @Test
    fun `test at lukket kandidatliste når noen fikk jobben registreres med status LUKKET`() {
        PresenterteKandidaterLytter(testRapid, presenterteKandidaterService)
        val stillingsId = UUID.randomUUID()
        val førsteAktørId = "6655443322"
        val førsteStillingstittel = "Stilling hvis kandidatliste ble lukket!"
        val meldingOmOpprettelseAvKandidatliste = meldingOmKandidathendelseDeltCv(førsteAktørId, førsteStillingstittel, stillingsId)

        testRapid.sendTestMessage(meldingOmOpprettelseAvKandidatliste)
        var kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste!!.tittel).isEqualTo(førsteStillingstittel)
        assertThat(kandidatliste.status).isEqualTo(Kandidatliste.Status.ÅPEN)

        val meldingOmKandidatlisteLukket = meldingOmKandidathendelseKandidatlisteLukketNoenFikkJobben(førsteAktørId, førsteStillingstittel, stillingsId)
        testRapid.sendTestMessage(meldingOmKandidatlisteLukket)

        kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste!!.status).isEqualTo(Kandidatliste.Status.LUKKET)
    }

    @Test
    fun `test at annullert kandidatliste registreres som slettet fra databasen`() {
        PresenterteKandidaterLytter(testRapid, presenterteKandidaterService)
        val stillingsId = UUID.randomUUID()
        val førsteAktørId = "44556677"
        val førsteStillingstittel = "Stilling hvis kandidatliste ble annullert!"
        val meldingOmOpprettelseAvKandidatliste = meldingOmKandidathendelseDeltCv(førsteAktørId, førsteStillingstittel, stillingsId)

        testRapid.sendTestMessage(meldingOmOpprettelseAvKandidatliste)
        var kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste!!.tittel).isEqualTo(førsteStillingstittel)
        assertThat(kandidatliste.slettet).isFalse

        val meldingOmKandidatlisteLukket = meldingOmKandidathendelseKandidatlisteAnnullert(førsteAktørId, førsteStillingstittel, stillingsId)
        testRapid.sendTestMessage(meldingOmKandidatlisteLukket)

        kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste!!.slettet).isTrue
    }

    @Test
    fun `test at kandidat slettes fra kandidatliste ved slettekandidathendelse`() {
        PresenterteKandidaterLytter(testRapid, presenterteKandidaterService)
        val stillingsId = UUID.randomUUID()
        val førsteAktørId = "44556677"
        val førsteStillingstittel = "Stilling hvis kandidat skal slettes fra!"
        val meldingOmOpprettelseAvKandidatliste = meldingOmKandidathendelseDeltCv(førsteAktørId, førsteStillingstittel, stillingsId)

        testRapid.sendTestMessage(meldingOmOpprettelseAvKandidatliste)
        var kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste!!.tittel).isEqualTo(førsteStillingstittel)
        var kandidat = repository.hentKandidat(førsteAktørId, kandidatliste.id!!)
        assertThat(kandidat!!.aktørId).isEqualTo(førsteAktørId)

        val meldingOmKandidatlisteLukket = meldingOmKandidathendelseKandidatSlettetFraListe(førsteAktørId, førsteStillingstittel, stillingsId)
        testRapid.sendTestMessage(meldingOmKandidatlisteLukket)

        kandidat = repository.hentKandidat(førsteAktørId, kandidatliste.id!!)
        assertThat(kandidat).isNull()
    }

    @Test
    fun `Skal sette listen som ÅPEN og slettet=true når vi får melding om kandidathendelse på en liste som allerede eksisterer`() {
        val aktørId = "2050897398605"
        val stillingsId = UUID.randomUUID()
        repository.lagre(Testdata.lagGyldigKandidatliste(stillingsId).copy(status = Kandidatliste.Status.LUKKET, slettet = true))

        val melding = meldingOmKandidathendelseDeltCv(aktørId = aktørId, stillingsId = stillingsId)
        testRapid.sendTestMessage(melding)

        PresenterteKandidaterLytter(testRapid, presenterteKandidaterService)
        val kandidatliste = repository.hentKandidatliste(stillingsId)

        // Verifiser kandidatliste
        assertNotNull(kandidatliste)
        assertThat(kandidatliste.slettet).isFalse()
        assertThat(kandidatliste.status).isEqualTo(Kandidatliste.Status.ÅPEN)
    }

    private fun meldingOmKandidathendelseDeltCv(
        aktørId: String,
        stillingstittel: String = "Noen skal få denne jobben!",
        stillingsId: UUID
    ) =
        """
            {
              "@event_name": "kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand",
              "kandidathendelse": {
                "type": "CV_DELT_VIA_REKRUTTERINGSBISTAND",
                "aktørId": "$aktørId",
                "organisasjonsnummer": "912998827",
                "kandidatlisteId": "08d56a3e-e1e2-4dfb-8078-363fe6489ea9",
                "tidspunkt": "2022-11-09T10:37:45.108+01:00",
                "stillingsId": "$stillingsId",
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
                  "image": "ghcr.io/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:88c48704fc1a9db29f744f7e9f6c7bad6c390e5b"
                },
                {
                  "id": "60bfc604-64ef-48b1-be1f-45ba5486a888",
                  "time": "2022-11-09T10:38:02.181523695",
                  "service": "rekrutteringsbistand-stilling-api",
                  "instance": "rekrutteringsbistand-stilling-api-544d69cf7b-cpvgs",
                  "image": "ghcr.io/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:88c48704fc1a9db29f744f7e9f6c7bad6c390e5b"
                }
              ],
              "stillingsinfo": {
                "stillingsinfoid": "ba9b1395-c7b5-4cdc-8060-d5b92ecde52e",
                "stillingsid": "$stillingsId",
                "eier": null,
                "notat": null,
                "stillingskategori": "STILLING"
              },
              "stilling": {
                "stillingstittel": "$stillingstittel"
              },
              "@forårsaket_av": {
                "id": "7becad81-fe66-4800-8bbe-abce2e4dbf75",
                "opprettet": "2022-11-09T10:38:00.057867691",
                "event_name": "kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand"
              }
            }
        """.trimIndent()

    private fun meldingOmKandidathendelseKandidatlisteLukketIngenFikkJobben(
        aktørId: String,
        stillingstittel: String = "Stilling hvis kandidatliste ble lukket!",
        stillingsId: UUID
    ) =
        """
            {
              "@event_name": "kandidat.kandidatliste-lukket-ingen-fikk-jobben",
              "kandidathendelse": {
                "type": "KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN",
                "aktørId": "$aktørId",
                "organisasjonsnummer": "912998827",
                "kandidatlisteId": "08d56a3e-e1e2-4dfb-8078-363fe6489ea9",
                "tidspunkt": "2022-11-09T10:37:45.108+01:00",
                "stillingsId": "$stillingsId",
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
                  "image": "ghcr.io/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:88c48704fc1a9db29f744f7e9f6c7bad6c390e5b"
                },
                {
                  "id": "60bfc604-64ef-48b1-be1f-45ba5486a888",
                  "time": "2022-11-09T10:38:02.181523695",
                  "service": "rekrutteringsbistand-stilling-api",
                  "instance": "rekrutteringsbistand-stilling-api-544d69cf7b-cpvgs",
                  "image": "ghcr.io/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:88c48704fc1a9db29f744f7e9f6c7bad6c390e5b"
                }
              ],
              "stillingsinfo": {
                "stillingsinfoid": "ba9b1395-c7b5-4cdc-8060-d5b92ecde52e",
                "stillingsid": "$stillingsId",
                "eier": null,
                "notat": null,
                "stillingskategori": "STILLING"
              },
              "stilling": {
                "stillingstittel": "$stillingstittel"
              },
              "@forårsaket_av": {
                "id": "7becad81-fe66-4800-8bbe-abce2e4dbf75",
                "opprettet": "2022-11-09T10:38:00.057867691",
                "event_name": "kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand"
              }
            }
        """.trimIndent()

    private fun meldingOmKandidathendelseKandidatlisteLukketNoenFikkJobben(
        aktørId: String,
        stillingstittel: String = "Stilling hvis kandidatliste ble lukket!",
        stillingsId: UUID
    ) =
        """
            {
              "@event_name": "kandidat.kandidatliste-lukket-noen-andre-fikk-jobben",
              "kandidathendelse": {
                "type": "KANDIDATLISTE_LUKKET_NOEN_ANDRE_FIKK_JOBBEN",
                "aktørId": "$aktørId",
                "organisasjonsnummer": "912998827",
                "kandidatlisteId": "08d56a3e-e1e2-4dfb-8078-363fe6489ea9",
                "tidspunkt": "2022-11-09T10:37:45.108+01:00",
                "stillingsId": "$stillingsId",
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
                  "image": "ghcr.io/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:88c48704fc1a9db29f744f7e9f6c7bad6c390e5b"
                },
                {
                  "id": "60bfc604-64ef-48b1-be1f-45ba5486a888",
                  "time": "2022-11-09T10:38:02.181523695",
                  "service": "rekrutteringsbistand-stilling-api",
                  "instance": "rekrutteringsbistand-stilling-api-544d69cf7b-cpvgs",
                  "image": "ghcr.io/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:88c48704fc1a9db29f744f7e9f6c7bad6c390e5b"
                }
              ],
              "stillingsinfo": {
                "stillingsinfoid": "ba9b1395-c7b5-4cdc-8060-d5b92ecde52e",
                "stillingsid": "$stillingsId",
                "eier": null,
                "notat": null,
                "stillingskategori": "STILLING"
              },
              "stilling": {
                "stillingstittel": "$stillingstittel"
              },
              "@forårsaket_av": {
                "id": "7becad81-fe66-4800-8bbe-abce2e4dbf75",
                "opprettet": "2022-11-09T10:38:00.057867691",
                "event_name": "kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand"
              }
            }
        """.trimIndent()

    private fun meldingOmKandidathendelseKandidatlisteAnnullert(
        aktørId: String,
        stillingstittel: String = "Stilling hvis kandidatliste ble annullert!",
        stillingsId: UUID
    ) =
        """
            {
              "@event_name": "kandidat.annullert",
              "kandidathendelse": {
                "type": "ANNULLERT",
                "aktørId": "$aktørId",
                "organisasjonsnummer": "912998827",
                "kandidatlisteId": "08d56a3e-e1e2-4dfb-8078-363fe6489ea9",
                "tidspunkt": "2022-11-09T10:37:45.108+01:00",
                "stillingsId": "$stillingsId",
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
                  "image": "ghcr.io/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:88c48704fc1a9db29f744f7e9f6c7bad6c390e5b"
                },
                {
                  "id": "60bfc604-64ef-48b1-be1f-45ba5486a888",
                  "time": "2022-11-09T10:38:02.181523695",
                  "service": "rekrutteringsbistand-stilling-api",
                  "instance": "rekrutteringsbistand-stilling-api-544d69cf7b-cpvgs",
                  "image": "ghcr.io/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:88c48704fc1a9db29f744f7e9f6c7bad6c390e5b"
                }
              ],
              "stillingsinfo": {
                "stillingsinfoid": "ba9b1395-c7b5-4cdc-8060-d5b92ecde52e",
                "stillingsid": "$stillingsId",
                "eier": null,
                "notat": null,
                "stillingskategori": "STILLING"
              },
              "stilling": {
                "stillingstittel": "$stillingstittel"
              },
              "@forårsaket_av": {
                "id": "7becad81-fe66-4800-8bbe-abce2e4dbf75",
                "opprettet": "2022-11-09T10:38:00.057867691",
                "event_name": "kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand"
              }
            }
        """.trimIndent()

    private fun meldingOmKandidathendelseKandidatSlettetFraListe(
        aktørId: String,
        stillingstittel: String = "Stilling hvis kandidat skal slettes fra!",
        stillingsId: UUID
    ) =
        """
            {
              "@event_name": "kandidat.slettet-fra-arbeidsgivers-kandidatliste",
              "kandidathendelse": {
                "type": "SLETTET_FRA_ARBEIDSGIVERS_KANDIDATLISTE",
                "aktørId": "$aktørId",
                "organisasjonsnummer": "912998827",
                "kandidatlisteId": "08d56a3e-e1e2-4dfb-8078-363fe6489ea9",
                "tidspunkt": "2022-11-09T10:37:45.108+01:00",
                "stillingsId": "$stillingsId",
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
                  "image": "ghcr.io/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:88c48704fc1a9db29f744f7e9f6c7bad6c390e5b"
                },
                {
                  "id": "60bfc604-64ef-48b1-be1f-45ba5486a888",
                  "time": "2022-11-09T10:38:02.181523695",
                  "service": "rekrutteringsbistand-stilling-api",
                  "instance": "rekrutteringsbistand-stilling-api-544d69cf7b-cpvgs",
                  "image": "ghcr.io/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:88c48704fc1a9db29f744f7e9f6c7bad6c390e5b"
                }
              ],
              "stillingsinfo": {
                "stillingsinfoid": "ba9b1395-c7b5-4cdc-8060-d5b92ecde52e",
                "stillingsid": "$stillingsId",
                "eier": null,
                "notat": null,
                "stillingskategori": "STILLING"
              },
              "stilling": {
                "stillingstittel": "$stillingstittel"
              },
              "@forårsaket_av": {
                "id": "7becad81-fe66-4800-8bbe-abce2e4dbf75",
                "opprettet": "2022-11-09T10:38:00.057867691",
                "event_name": "kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand"
              }
            }
        """.trimIndent()
}

