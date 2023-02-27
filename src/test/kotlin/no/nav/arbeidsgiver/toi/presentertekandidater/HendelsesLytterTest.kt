package no.nav.arbeidsgiver.toi.presentertekandidater

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.HendelsesLytter
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.NotifikasjonPubliserer
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.PresenterteKandidaterLytter
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.PresenterteKandidaterService
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidatliste
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HendelsesLytterTest {
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    private val presenterteKandidaterService = PresenterteKandidaterService(repository)
    private lateinit var logWatcher: ListAppender<ILoggingEvent>
    private val testRapid = TestRapid()

    @BeforeAll
    fun init() {
        startLocalApplication()
        setUpLogWatcher()
        HendelsesLytter(
            testRapid,
            NotifikasjonPubliserer(testRapid),
            PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
            presenterteKandidaterService
        )
    }

    @BeforeEach
    fun setUp() {
        testRapid.reset()
    }

    private fun setUpLogWatcher() {
        logWatcher = ListAppender<ILoggingEvent>()
        logWatcher.start()
        val logger =
            LoggerFactory.getLogger(PresenterteKandidaterLytter::class.java.name) as ch.qos.logback.classic.Logger
        logger.addAppender(logWatcher)
    }

    @Test
    fun `Skal lagre kandidatliste og kandidat når vi får melding om kandidathendelse`() {
        val aktørIder = listOf("2040897398605", "1340897398605")
        val stillingsId = UUID.randomUUID()
        val melding = meldingDelCv(aktørIder, stillingsId)

        testRapid.sendTestMessage(melding)

        val kandidatliste =
            repository.hentKandidatliste(stillingsId)
        val kandidater = repository.hentKandidater(kandidatliste?.id!!)

        // Verifiser kandidatliste
        assertNotNull(kandidatliste)
        assertNotNull(kandidatliste.uuid)
        assertThat(kandidatliste.stillingId).isEqualTo(stillingsId)
        assertThat(kandidatliste.tittel).isEqualTo("En fantastisk stilling")
        assertThat(kandidatliste.status).isEqualTo(Kandidatliste.Status.ÅPEN)
        assertThat(kandidatliste.slettet).isFalse
        assertThat(kandidatliste.virksomhetsnummer).isEqualTo("312113341")
        assertNotNull(kandidatliste.id)

        // Verifiser kandidater
        assertThat(kandidater).hasSize(2)

        val førsteKandidat = kandidater.first()
        assertNotNull(førsteKandidat.id)
        assertThat(førsteKandidat.aktørId).isEqualTo(aktørIder.first())
        assertThat(førsteKandidat.kandidatlisteId).isEqualTo(kandidatliste.id)
        assertNotNull(førsteKandidat.uuid)

        val andreKandidat = kandidater[1]
        assertNotNull(førsteKandidat.id)
        assertThat(andreKandidat.aktørId).isEqualTo(aktørIder[1])
        assertThat(andreKandidat.kandidatlisteId).isEqualTo(kandidatliste.id)
        assertNotNull(andreKandidat.uuid)
    }

    @Test
    fun `Konsumering av melding skal være idempotent`() {
        val aktørId = "2140897398605"
        val stillingsId = UUID.randomUUID()
        val melding = meldingDelCv(aktørIder = listOf(aktørId), stillingsId = stillingsId)

        testRapid.sendTestMessage(melding)

        // Verifiser etter første melding
        val kandidatlisteEtterFørsteMelding = repository.hentKandidatliste(stillingsId)
        val kandidaterEtterFørsteMelding = repository.hentKandidater(kandidatlisteEtterFørsteMelding?.id!!)

        assertNotNull(kandidatlisteEtterFørsteMelding)
        assertNotNull(kandidatlisteEtterFørsteMelding.id)
        assertNotNull(kandidatlisteEtterFørsteMelding.uuid)
        assertThat(kandidatlisteEtterFørsteMelding.stillingId).isEqualTo(stillingsId)
        assertThat(kandidatlisteEtterFørsteMelding.tittel).isEqualTo("En fantastisk stilling")
        assertThat(kandidatlisteEtterFørsteMelding.status).isEqualTo(Kandidatliste.Status.ÅPEN)
        assertThat(kandidatlisteEtterFørsteMelding.slettet).isFalse
        assertThat(kandidatlisteEtterFørsteMelding.virksomhetsnummer).isEqualTo("312113341")
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
        assertThat(kandidatlisteEtterAndreMelding.tittel).isEqualTo("En fantastisk stilling")
        assertThat(kandidatlisteEtterAndreMelding.status).isEqualTo(Kandidatliste.Status.ÅPEN)
        assertThat(kandidatlisteEtterAndreMelding.slettet).isFalse
        assertThat(kandidatlisteEtterAndreMelding.virksomhetsnummer).isEqualTo("312113341")

        assertThat(kandidaterEtterAndreMelding).hasSize(1)
        val kandidatEtterAndreMelding = kandidaterEtterAndreMelding.first()
        assertNotNull(kandidatEtterAndreMelding.id)
        assertThat(kandidatEtterAndreMelding.aktørId).isEqualTo(aktørId)
        assertThat(kandidatEtterAndreMelding.kandidatlisteId).isEqualTo(kandidatEtterAndreMelding.kandidatlisteId)
        assertNotNull(kandidatEtterAndreMelding.uuid)
    }

    @Test
    fun `Skal sende notifikasjonsmelding når CV-delt-melding med CvDeltData mottas`() {
        val stillingsId = UUID.randomUUID()
        val melding = meldingDelCv(aktørIder = listOf("2210897398605", "2810897398605"), stillingsId = stillingsId)

        testRapid.sendTestMessage(melding)

        val inspektør = testRapid.inspektør
        assertThat(inspektør.size).isEqualTo(1)
        val notifikasjonsmeldingjJsonNode = inspektør.message(0)
        assertThat(notifikasjonsmeldingjJsonNode["@event_name"].asText()).isEqualTo("notifikasjon.cv-delt")
        assertThat(notifikasjonsmeldingjJsonNode["notifikasjonsId"].asText()).isEqualTo("$stillingsId-2023-02-09T09:45:53.649+01:00[Europe/Oslo]")
        assertThat(notifikasjonsmeldingjJsonNode["stillingsId"].asText()).isEqualTo(stillingsId.toString())
        assertThat(notifikasjonsmeldingjJsonNode["virksomhetsnummer"].asText()).isEqualTo("312113341")
        assertThat(notifikasjonsmeldingjJsonNode["utførtAvVeilederFornavn"].asText()).isEqualTo("Veileder")
        assertThat(notifikasjonsmeldingjJsonNode["utførtAvVeilederEtternavn"].asText()).isEqualTo("Veiledersen")
        assertThat(notifikasjonsmeldingjJsonNode["arbeidsgiversEpostadresser"].toList().map { it.asText() }).containsExactlyInAnyOrder("hei@arbeidsgiversdomene.no", "enansatt@trygdeetaten.no")
        assertThat(notifikasjonsmeldingjJsonNode["meldingTilArbeidsgiver"].asText()).isEqualTo("Hei, her er en\ngod kandidat som vil føre til at du kan selge varene dine med høyere avanse!")
        assertThat(notifikasjonsmeldingjJsonNode["stillingstittel"].asText()).isEqualTo("En fantastisk stilling")
        assertThat(notifikasjonsmeldingjJsonNode["tidspunktForHendelse"].asText()).isEqualTo("2023-02-09T09:45:53.649+01:00")
        assertThat(notifikasjonsmeldingjJsonNode["@slutt_av_hendelseskjede"]).isNull()
    }

    @Test
    fun `Hvis databaselagring feiler ved mottak av kandidathendelse skal det ikke publiseres notifikasjonsmelding`() {
        val melding = meldingDelCv(
            aktørIder = listOf("UGYLDIG_AKTØR_ID_SOM_ER_ALTFOR_LANG_FOR_DB"),
            stillingsId = UUID.randomUUID()
        )

        assertThrows<Exception> {
            testRapid.sendTestMessage(melding)
        }

        assertThat(testRapid.inspektør.size).isEqualTo(0)
    }

    @Test
    fun `Når vi mottar CvDelt-melding om en kandidatliste vi allerede har lagret, men med endrede opplysninger, skal den oppdateres`() {
        val stillingsId = UUID.randomUUID()

        val førsteAktørId = "2040897398605"
        val førsteStillingstittel = "Klovn søkes"
        val førsteKandidathendelsesmelding = meldingDelCv(listOf(førsteAktørId), stillingsId, førsteStillingstittel)

        testRapid.sendTestMessage(førsteKandidathendelsesmelding)
        val kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste!!.tittel).isEqualTo(førsteStillingstittel)

        val andreAktørId = "2040897398605"
        val andreStillingstittel = "Hoffnarr søkes"
        val andreKandidathendelsesmelding = meldingDelCv(listOf(andreAktørId), stillingsId, andreStillingstittel)

        testRapid.sendTestMessage(andreKandidathendelsesmelding)
        val oppdatertKandidatliste =
            repository.hentKandidatliste(stillingsId)
        assertThat(oppdatertKandidatliste!!.tittel).isEqualTo(andreStillingstittel)
    }

    @Test
    fun `Ved mottak av slutt_av_hendelseskjede satt til true skal det ikke legges ut ny hendelse på rapid`() {
        val aktørId = "2040897398605"
        val stillingsId = UUID.randomUUID()
        val melding = meldingDelCv(aktørIder = listOf(aktørId), stillingsId = stillingsId, sluttAvHendelseskjede = true)

        testRapid.sendTestMessage(melding)

        assertThat(testRapid.inspektør.size).isEqualTo(0)
    }

    private fun meldingDelCv(
        aktørIder: List<String>,
        stillingsId: UUID,
        stillingstittel: String = "En fantastisk stilling",
        sluttAvHendelseskjede: Boolean = false) = """
        {
          "stillingstittel": "$stillingstittel",
          "organisasjonsnummer": "312113341",
          "kandidatlisteId": "d5b5b4c1-0375-4719-9038-ab31fe27fb40",
          "tidspunkt": "2023-02-09T09:45:53.649+01:00",
          "stillingsId": "$stillingsId",
          "utførtAvNavIdent": "Z994633",
          "utførtAvNavKontorKode": "0313",
          "utførtAvVeilederFornavn": "Veileder",
          "utførtAvVeilederEtternavn": "Veiledersen",
          "arbeidsgiversEpostadresser": [
            "hei@arbeidsgiversdomene.no",
            "enansatt@trygdeetaten.no"
          ],
          "meldingTilArbeidsgiver": "Hei, her er en\ngod kandidat som vil føre til at du kan selge varene dine med høyere avanse!",
          "kandidater": {
            ${aktørIder.map {
                """
                            "$it": {
                              "harHullICv": false,
                              "alder": 51,
                              "tilretteleggingsbehov": [],
                              "innsatsbehov": "BFORM",
                              "hovedmål": "BEHOLDEA"
                            }
                        """.trimIndent()
            }.joinToString(separator = ",")}
          },
          "@event_name": "kandidat_v2.DelCvMedArbeidsgiver",
          "stillingsinfo": {
            "stillingsinfoid": "d55c3510-d263-42da-8785-3c92d3eb8732",
            "stillingsid": "$stillingsId",
            "eier": null,
            "notat": null,
            "stillingskategori": "STILLING"
          },
          "stilling": {
            "stillingstittel": "$stillingstittel"
          }
          ${if (sluttAvHendelseskjede) """ ""@slutt_av_hendelseskjede": true""" else ""}
        }
    """.trimIndent()

}