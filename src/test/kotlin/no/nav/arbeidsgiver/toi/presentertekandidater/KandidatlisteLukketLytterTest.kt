package no.nav.arbeidsgiver.toi.presentertekandidater

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.CvDeltLytter
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.NotifikasjonPubliserer
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.PresenterteKandidaterLytter
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.PresenterteKandidaterService
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class KandidatlisteLukketLytterTest {
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    private val presenterteKandidaterService = PresenterteKandidaterService(repository)
    private lateinit var logWatcher: ListAppender<ILoggingEvent>
    private val testRapid = TestRapid()

    @BeforeAll
    fun init() {
        startLocalApplication()
        setUpLogWatcher()
        CvDeltLytter(
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
    fun `LukketKandidatliste-melding skal føre til at kandidatlista settes til status LUKKET`() {

    }

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