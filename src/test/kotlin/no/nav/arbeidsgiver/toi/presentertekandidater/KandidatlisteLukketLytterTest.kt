package no.nav.arbeidsgiver.toi.presentertekandidater

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.PresenterteKandidaterLytter
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.PresenterteKandidaterService
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidatliste
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KandidatlisteLukketLytterTest {
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    private val presenterteKandidaterService = PresenterteKandidaterService(repository)
    private lateinit var logWatcher: ListAppender<ILoggingEvent>

    @BeforeAll
    fun init() {
        startLocalApplication()
        setUpLogWatcher()
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
    fun `LukketKandidatliste-melding skal føre til at kandidatlista settes til status LUKKET`() {
        val stillingsId = UUID.randomUUID()
        val aktørId = "1122334455"
        val organisasjonsnummer = "123"
        val kandidatListe = repository.lagre(Kandidatliste.ny(stillingsId, "en stilling", organisasjonsnummer))
        repository.lagre(
            Kandidat(
                uuid = UUID.randomUUID(),
                aktørId = aktørId,
                kandidatlisteId = kandidatListe.id!!,
                arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING
            )
        )

        testRapid.sendTestMessage(kandidatlisteLukket(stillingsId, organisasjonsnummer))

        val oppdatertKandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(oppdatertKandidatliste!!.status).isEqualTo(Kandidatliste.Status.LUKKET)
    }

    private fun kandidatlisteLukket(
        stillingsId: UUID,
        organisasjonsNummer: String
    ) = """
        {
          "aktørIderFikkJobben": [],
          "aktørIderFikkIkkeJobben": [],
          "organisasjonsnummer": "$organisasjonsNummer",
          "kandidatlisteId": "f3f4a72b-1388-4a1b-b808-ed6336e2c6a4",
          "tidspunkt": "2023-02-21T08:38:01.053+01:00",
          "stillingsId": "$stillingsId",
          "utførtAvNavIdent": "A000000",
          "@event_name": "kandidat_v2.LukketKandidatliste",
          "@id": "7fa7ab9a-d016-4ed2-9f9a-d1a1ad7018f1",
          "@opprettet": "2023-02-21T08:39:01.937854240",
          "system_read_count": 0
        }
    """.trimIndent()

    /*
        @Test
    fun `test at lukket kandidatliste når ingen har fått jobben registreres med status LUKKET`() {
        val stillingsId = UUID.randomUUID()
        val aktørId = "1122334455"
        val meldingOmOpprettelseAvKandidatliste =
            meldingOmKandidathendelseDeltCv(aktørId = aktørId, stillingsId = stillingsId)

        testRapid.sendTestMessage(meldingOmOpprettelseAvKandidatliste)
        var kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste?.status).isEqualTo(Kandidatliste.Status.ÅPEN)

        val meldingOmKandidatlisteLukket =
            meldingOmKandidathendelseKandidatlisteLukketIngenFikkJobben(aktørId, stillingsId)
        testRapid.sendTestMessage(meldingOmKandidatlisteLukket)

        kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste!!.status).isEqualTo(Kandidatliste.Status.LUKKET)
    }

    @Test
    fun `test at lukket kandidatliste når noen fikk jobben registreres med status LUKKET`() {
        val stillingsId = UUID.randomUUID()
        val aktørId = "6655443322"
        val meldingOmOpprettelseAvKandidatliste =
            meldingOmKandidathendelseDeltCv(aktørId = aktørId, stillingsId = stillingsId)

        testRapid.sendTestMessage(meldingOmOpprettelseAvKandidatliste)
        var kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste?.status).isEqualTo(Kandidatliste.Status.ÅPEN)

        val meldingOmKandidatlisteLukket =
            meldingOmKandidathendelseKandidatlisteLukketNoenFikkJobben(aktørId, stillingsId)
        testRapid.sendTestMessage(meldingOmKandidatlisteLukket)

        kandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(kandidatliste!!.status).isEqualTo(Kandidatliste.Status.LUKKET)
    }
     */
}