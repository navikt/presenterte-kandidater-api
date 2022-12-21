package no.nav.arbeidsgiver.toi.presentertekandidater

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.PresenterteKandidaterLytter
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.PresenterteKandidaterService
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidatliste
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PresenterteKandidaterLytterTest {
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    private val presenterteKandidaterService = PresenterteKandidaterService(repository)
    lateinit var logWatcher: ListAppender<ILoggingEvent>

    @BeforeAll
    fun init() {
        startLocalApplication()
        setUpLogWatcher()
    }

    private fun setUpLogWatcher() {
        logWatcher = ListAppender<ILoggingEvent>()
        logWatcher.start()
        val logger = LoggerFactory.getLogger(PresenterteKandidaterLytter::class.java.name) as ch.qos.logback.classic.Logger
        logger.addAppender(logWatcher)
    }

    @Test
    fun `Skal lagre kandidatliste og kandidat når vi får melding om kandidathendelse`() {
        val aktørId = "2040897398605"
        val stillingsId = UUID.randomUUID()
        val melding = meldingOmKandidathendelseDeltCv(aktørId = aktørId, stillingsId = stillingsId)

        testRapid.sendTestMessage(melding)

        PresenterteKandidaterLytter(testRapid, PrometheusMeterRegistry(PrometheusConfig.DEFAULT), presenterteKandidaterService)
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
        PresenterteKandidaterLytter(testRapid, PrometheusMeterRegistry(PrometheusConfig.DEFAULT), presenterteKandidaterService)

        // Verifiser etter første melding
        val kandidatlisteEtterFørsteMelding = repository.hentKandidatliste(stillingsId)
        val kandidaterEtterFørsteMelding = repository.hentKandidater(kandidatlisteEtterFørsteMelding?.id!!)

        assertNotNull(kandidatlisteEtterFørsteMelding)
        assertNotNull(kandidatlisteEtterFørsteMelding.id)
        assertNotNull(kandidatlisteEtterFørsteMelding.uuid)
        assertThat(kandidatlisteEtterFørsteMelding.stillingId).isEqualTo(stillingsId)
        assertThat(kandidatlisteEtterFørsteMelding.tittel).isEqualTo("Noen skal få denne jobben!")
        assertThat(kandidatlisteEtterFørsteMelding.status).isEqualTo(Kandidatliste.Status.ÅPEN)
        assertThat(kandidatlisteEtterFørsteMelding.slettet).isFalse
        assertThat(kandidatlisteEtterFørsteMelding.virksomhetsnummer).isEqualTo("912998827")
        assertThat(kandidaterEtterFørsteMelding).hasSize(1)

        val kandidatEtterFørsteMelding = kandidaterEtterFørsteMelding.first()
        assertNotNull(kandidatEtterFørsteMelding.id)
        assertThat(kandidatEtterFørsteMelding.aktørId).isEqualTo(aktørId)
        assertThat(kandidatEtterFørsteMelding.kandidatlisteId).isEqualTo(kandidatlisteEtterFørsteMelding.id)
        assertNotNull(kandidatEtterFørsteMelding.uuid)

        // Send ny melding
        testRapid.sendTestMessage(melding)

        // Asserts etter andre melding
        val kandidatlisteEtterAndreMelding = repository.hentKandidatliste(stillingsId)
        val kandidaterEtterAndreMelding = repository.hentKandidater(kandidatlisteEtterAndreMelding?.id!!)

        assertNotNull(kandidatlisteEtterAndreMelding)
        assertThat(kandidatlisteEtterAndreMelding.id).isEqualTo(kandidatlisteEtterAndreMelding.id)
        assertThat(kandidatlisteEtterAndreMelding.uuid).isEqualTo(kandidatlisteEtterAndreMelding.uuid)
        assertThat(kandidatlisteEtterAndreMelding.stillingId).isEqualTo(stillingsId)
        assertThat(kandidatlisteEtterAndreMelding.tittel).isEqualTo("Noen skal få denne jobben!")
        assertThat(kandidatlisteEtterAndreMelding.status).isEqualTo(Kandidatliste.Status.ÅPEN)
        assertThat(kandidatlisteEtterAndreMelding.slettet).isFalse
        assertThat(kandidatlisteEtterAndreMelding.virksomhetsnummer).isEqualTo("912998827")

        assertThat(kandidaterEtterAndreMelding).hasSize(1)
        val kandidatEtterAndreMelding = kandidaterEtterAndreMelding.first()
        assertNotNull(kandidatEtterAndreMelding.id)
        assertThat(kandidatEtterAndreMelding.aktørId).isEqualTo(aktørId)
        assertThat(kandidatEtterAndreMelding.kandidatlisteId).isEqualTo(kandidatEtterAndreMelding.kandidatlisteId)
        assertNotNull(kandidatEtterAndreMelding.uuid)
    }

    @Test
    fun `Skal lagre begge kandidater når vi får meldinger om kandidathendelser som gjelder samme kandidatliste`() {
        PresenterteKandidaterLytter(testRapid, PrometheusMeterRegistry(PrometheusConfig.DEFAULT), presenterteKandidaterService)
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
        PresenterteKandidaterLytter(testRapid, PrometheusMeterRegistry(PrometheusConfig.DEFAULT), presenterteKandidaterService)
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
        PresenterteKandidaterLytter(testRapid, PrometheusMeterRegistry(PrometheusConfig.DEFAULT), presenterteKandidaterService)
        val stillingsId = UUID.randomUUID()
        val aktørId = "1122334455"
        val meldingOmOpprettelseAvKandidatliste = meldingOmKandidathendelseDeltCv(aktørId = aktørId, stillingsId = stillingsId)

        testRapid.sendTestMessage(meldingOmOpprettelseAvKandidatliste)
        var kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste?.status).isEqualTo(Kandidatliste.Status.ÅPEN)

        val meldingOmKandidatlisteLukket = meldingOmKandidathendelseKandidatlisteLukketIngenFikkJobben(aktørId, stillingsId)
        testRapid.sendTestMessage(meldingOmKandidatlisteLukket)

        kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste!!.status).isEqualTo(Kandidatliste.Status.LUKKET)
    }

    @Test
    fun `test at lukket kandidatliste når noen fikk jobben registreres med status LUKKET`() {
        PresenterteKandidaterLytter(testRapid, PrometheusMeterRegistry(PrometheusConfig.DEFAULT), presenterteKandidaterService)
        val stillingsId = UUID.randomUUID()
        val aktørId = "6655443322"
        val meldingOmOpprettelseAvKandidatliste = meldingOmKandidathendelseDeltCv(aktørId = aktørId, stillingsId = stillingsId)

        testRapid.sendTestMessage(meldingOmOpprettelseAvKandidatliste)
        var kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste?.status).isEqualTo(Kandidatliste.Status.ÅPEN)

        val meldingOmKandidatlisteLukket = meldingOmKandidathendelseKandidatlisteLukketNoenFikkJobben(aktørId, stillingsId)
        testRapid.sendTestMessage(meldingOmKandidatlisteLukket)

        kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste!!.status).isEqualTo(Kandidatliste.Status.LUKKET)
    }

    @Test
    fun `test at annullert kandidatliste registreres som slettet fra databasen`() {
        PresenterteKandidaterLytter(testRapid, PrometheusMeterRegistry(PrometheusConfig.DEFAULT), presenterteKandidaterService)
        val stillingsId = UUID.randomUUID()
        val aktørId = "44556677"
        val meldingOmOpprettelseAvKandidatliste = meldingOmKandidathendelseDeltCv(aktørId = aktørId, stillingsId = stillingsId)

        testRapid.sendTestMessage(meldingOmOpprettelseAvKandidatliste)
        var kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste?.slettet).isFalse

        val meldingOmKandidatlisteLukket = meldingOmKandidathendelseKandidatlisteAnnullert(aktørId, stillingsId)
        testRapid.sendTestMessage(meldingOmKandidatlisteLukket)

        kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste!!.slettet).isTrue
    }

    @Test
    fun `test at kandidat slettes fra kandidatliste ved slettekandidathendelse`() {
        PresenterteKandidaterLytter(testRapid, PrometheusMeterRegistry(PrometheusConfig.DEFAULT), presenterteKandidaterService)
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
        repository.lagre(lagGyldigKandidatliste(stillingsId).copy(status = Kandidatliste.Status.LUKKET, slettet = true))

        val melding = meldingOmKandidathendelseDeltCv(aktørId = aktørId, stillingsId = stillingsId)
        testRapid.sendTestMessage(melding)

        PresenterteKandidaterLytter(testRapid, PrometheusMeterRegistry(PrometheusConfig.DEFAULT), presenterteKandidaterService)
        val kandidatliste = repository.hentKandidatliste(stillingsId)

        // Verifiser kandidatliste
        assertNotNull(kandidatliste)
        assertThat(kandidatliste.slettet).isFalse
        assertThat(kandidatliste.status).isEqualTo(Kandidatliste.Status.ÅPEN)
    }

    @Test
    fun `Hvis noe feiler ved mottak av kandidathendelse skal dette catches og logges`() {
        val stillingsIdSomVilFeile = "ikke-gyldig-UUID"

        val meldingSomVilFeile = meldingSomKanFeileVedUgyldigStillingsId(stillingsId = stillingsIdSomVilFeile)
        testRapid.sendTestMessage(meldingSomVilFeile)

        PresenterteKandidaterLytter(testRapid, PrometheusMeterRegistry(PrometheusConfig.DEFAULT), presenterteKandidaterService)
        assertThat(logWatcher.list).isNotEmpty
        assertThat(logWatcher.list[logWatcher.list.size - 1].message).isEqualTo("Feil ved mottak av kandidathendelse. Dette må håndteres og man må resette offset for å lese meldingen på nytt.")
    }

    private fun lagGyldigKandidatliste(stillingsId: UUID) : Kandidatliste = Kandidatliste(
        id = null,
        uuid = UUID.randomUUID(),
        stillingsId, "Tittel",
        Kandidatliste.Status.ÅPEN,
        false,
        "",
        ZonedDateTime.now(),
        ZonedDateTime.now())

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

    private fun meldingSomKanFeileVedUgyldigStillingsId(stillingsId: String) =
        """
            {
              "@event_name": "kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand",
              "kandidathendelse": {
                "type": "CV_DELT_VIA_REKRUTTERINGSBISTAND",
                "aktørId": "112233",
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
                "stillingstittel": "Melding som kan feile"
              },
              "@forårsaket_av": {
                "id": "7becad81-fe66-4800-8bbe-abce2e4dbf75",
                "opprettet": "2022-11-09T10:38:00.057867691",
                "event_name": "kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand"
              }
            }
        """.trimIndent()

    private fun meldingOmKandidathendelseKandidatlisteLukketIngenFikkJobben(aktørId: String, stillingsId: UUID) =
        """
          {
              "@event_name": "kandidat.kandidatliste-lukket-ingen-fikk-jobben",
              "kandidathendelse": {
                "type": "KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN",
                "aktørId": "$aktørId",
                "organisasjonsnummer": "987986506",
                "kandidatlisteId": "34b347d3-4a9a-4689-8483-1bf46a6fc7db",
                "tidspunkt": "2022-11-29T14:59:37.452+01:00",
                "stillingsId": "$stillingsId",
                "utførtAvNavIdent": "Z994633",
                "utførtAvNavKontorKode": "",
                "synligKandidat": true,
                "harHullICv": false,
                "alder": 24,
                "tilretteleggingsbehov": [
                  "arbeidstid",
                  "arbeidshverdagen",
                  "utfordringerMedNorsk"
                ]
              },
              "@id": "b9bf6bb6-d9f7-411b-bcd3-edad8acbb965",
              "@opprettet": "2022-11-29T15:00:02.133974153",
              "system_read_count": 0,
              "system_participating_services": [
                {
                  "id": "6e07eb50-f63b-482e-8663-293254817bec",
                  "time": "2022-11-29T15:00:02.031970926",
                  "service": "rekrutteringsbistand-stilling-api",
                  "instance": "rekrutteringsbistand-stilling-api-7bf9cf86c9-4q8rl",
                  "image": "ghcr.io/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:f7d84b053937e9dba1d07e44922c0523d1be6a93"
                },
                {
                  "id": "b9bf6bb6-d9f7-411b-bcd3-edad8acbb965",
                  "time": "2022-11-29T15:00:02.133974153",
                  "service": "rekrutteringsbistand-stilling-api",
                  "instance": "rekrutteringsbistand-stilling-api-7bf9cf86c9-4q8rl",
                  "image": "ghcr.io/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:f7d84b053937e9dba1d07e44922c0523d1be6a93"
                }
              ],
              "stillingsinfo": {
                "stillingsinfoid": "35416d6a-ec58-435b-92ad-6d5b7116bd50",
                "stillingsid": "$stillingsId",
                "eier": {
                  "navident": "Z994633",
                  "navn": "F_Z994633 E_Z994633"
                },
                "notat": null,
                "stillingskategori": "STILLING"
              },
              "stilling": {
                "stillingstittel": "Saksbehandler habilitering"
              },
              "@forårsaket_av": {
                "id": "6e07eb50-f63b-482e-8663-293254817bec",
                "opprettet": "2022-11-29T15:00:02.031970926",
                "event_name": "kandidat.kandidatliste-lukket-ingen-fikk-jobben"
              }
            }
        """.trimIndent()

    private fun meldingOmKandidathendelseKandidatlisteLukketNoenFikkJobben(aktørId: String, stillingsId: UUID) =
        """
            {
              "@event_name": "kandidat.kandidatliste-lukket-noen-andre-fikk-jobben",
              "kandidathendelse": {
                "type": "KANDIDATLISTE_LUKKET_NOEN_ANDRE_FIKK_JOBBEN",
                "aktørId": "$aktørId",
                "organisasjonsnummer": "889524502",
                "kandidatlisteId": "b72488d6-2e27-4bd6-aaca-3637534e2282",
                "tidspunkt": "2022-11-29T15:01:35.118+01:00",
                "stillingsId": "$stillingsId",
                "utførtAvNavIdent": "Z994633",
                "utførtAvNavKontorKode": "",
                "synligKandidat": true,
                "harHullICv": true,
                "alder": 53,
                "tilretteleggingsbehov": []
              },
              "@id": "2c13455d-6071-410c-849e-bfc9eb320636",
              "@opprettet": "2022-11-29T15:02:02.530833275",
              "system_read_count": 0,
              "system_participating_services": [
                {
                  "id": "bc84d415-8c21-43bc-b6b3-6d87aa458abc",
                  "time": "2022-11-29T15:02:02.444969919",
                  "service": "rekrutteringsbistand-stilling-api",
                  "instance": "rekrutteringsbistand-stilling-api-7bf9cf86c9-4q8rl",
                  "image": "ghcr.io/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:f7d84b053937e9dba1d07e44922c0523d1be6a93"
                },
                {
                  "id": "2c13455d-6071-410c-849e-bfc9eb320636",
                  "time": "2022-11-29T15:02:02.530833275",
                  "service": "rekrutteringsbistand-stilling-api",
                  "instance": "rekrutteringsbistand-stilling-api-7bf9cf86c9-4q8rl",
                  "image": "ghcr.io/navikt/rekrutteringsbistand-stilling-api/rekrutteringsbistand-stilling-api:f7d84b053937e9dba1d07e44922c0523d1be6a93"
                }
              ],
              "stillingsinfo": {
                "stillingsinfoid": "ce1c8c60-dc74-48f5-98d3-851d3a18e5aa",
                "stillingsid": "$stillingsId",
                "eier": null,
                "notat": null,
                "stillingskategori": "STILLING"
              },
              "stilling": {
                "stillingstittel": "Engasjert utvikler søkes"
              },
              "@forårsaket_av": {
                "id": "bc84d415-8c21-43bc-b6b3-6d87aa458abc",
                "opprettet": "2022-11-29T15:02:02.444969919",
                "event_name": "kandidat.kandidatliste-lukket-noen-andre-fikk-jobben"
              }
            }        
        """.trimIndent()

    private fun meldingOmKandidathendelseKandidatlisteAnnullert(aktørId: String, stillingsId: UUID) =
        """
          {
              "@event_name": "kandidat.annullert",
              "kandidathendelse": {
                "type": "ANNULLERT",
                "aktørId": "$aktørId",
                "organisasjonsnummer": "893119302",
                "kandidatlisteId": "b62e39cc-f2f1-4093-bd28-e7fa929cc0bc",
                "tidspunkt": "2022-11-29T15:35:34.237+01:00",
                "stillingsId": "$stillingsId",
                "utførtAvNavIdent": "Z994633",
                "utførtAvNavKontorKode": "",
                "synligKandidat": true,
                "harHullICv": false,
                "alder": 61,
                "tilretteleggingsbehov": []
              },
                "stillingsinfo": {
                  "stillingsinfoid": "ce1c8c60-dc74-48f5-98d3-851d3a18e5aa",
                  "stillingsid": "$stillingsId",
                  "eier": null,
                  "notat": null,
                  "stillingskategori": "STILLING"
                },
                "stilling": {
                  "stillingstittel": "Engasjert utvikler søkes"
                },
                  "@forårsaket_av": {
                    "id": "bc84d415-8c21-43bc-b6b3-6d87aa458abc",
                    "opprettet": "2022-11-29T15:02:02.444969919",
                    "event_name": "kandidat.annullert"
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

