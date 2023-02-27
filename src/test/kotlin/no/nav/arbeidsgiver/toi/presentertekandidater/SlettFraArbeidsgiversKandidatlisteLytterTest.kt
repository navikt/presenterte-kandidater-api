package no.nav.arbeidsgiver.toi.presentertekandidater

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.PresenterteKandidaterLytter
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
class SlettFraArbeidsgiversKandidatlisteLytterTest {
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
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


    @Test
    fun `test at kandidat slettes fra kandidatliste ved slettekandidathendelse`() {
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
        val meldingOmSlettFraArbeidsgiversKandidatliste =
            slettFraArbeidsgiversKandidatliste(aktørId = aktørId, kandidatlisteId = UUID.randomUUID().toString(), stillingsId.toString())
        testRapid.sendTestMessage(meldingOmSlettFraArbeidsgiversKandidatliste)

        val kandidat = repository.hentKandidat(aktørId, kandidatListe.id!!)
        assertThat(kandidat).isNull()
    }

    private fun slettFraArbeidsgiversKandidatliste(aktørId: String, kandidatlisteId: String, stillingsId: String, sluttkvittering: Boolean = false) = """
        {
          "aktørId": "$aktørId",
          "organisasjonsnummer": "312113341",
          "kandidatlisteId": "$kandidatlisteId",
          "tidspunkt": "2023-02-27T14:23:28.004+01:00",
          "stillingsId": "$stillingsId",
          "utførtAvNavIdent": "Z994944",
          "utførtAvNavKontorKode": "1001",
          "@event_name": "kandidat_v2.SlettFraArbeidsgiversKandidatliste"
          ${if (!sluttkvittering) "" else """, "@slutt_av_hendelseskjede": $sluttkvittering"""}
        }
    """.trimIndent()

    @Test
    fun `Ved mottak av slettethendelse der slutt_av_hendelseskjede ikke er satt skal dette settes og republiseres`() {
        val melding =
            slettFraArbeidsgiversKandidatliste(aktørId = "enAktørId", kandidatlisteId = UUID.randomUUID().toString(), stillingsId = UUID.randomUUID().toString())

        testRapid.sendTestMessage(melding)

        assertThat(testRapid.inspektør.size).isEqualTo(1)
        assertThat(testRapid.inspektør.message(0)["@slutt_av_hendelseskjede"].asBoolean()).isTrue
    }

    @Test
    fun `Skal ikke behandle meldinger med slutt_av_hendelseskjede satt til true`() {
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
        val meldingOmSlettFraArbeidsgiversKandidatliste =
            slettFraArbeidsgiversKandidatliste(aktørId = aktørId, kandidatlisteId = UUID.randomUUID().toString(), stillingsId.toString(), sluttkvittering = true)
        testRapid.sendTestMessage(meldingOmSlettFraArbeidsgiversKandidatliste)

        val kandidat = repository.hentKandidat(aktørId, kandidatListe.id!!)
        assertThat(kandidat).isNotNull

        assertThat(testRapid.inspektør.size).isEqualTo(0)
    }

    private fun setUpLogWatcher() {
        logWatcher = ListAppender<ILoggingEvent>()
        logWatcher.start()
        val logger =
            LoggerFactory.getLogger(PresenterteKandidaterLytter::class.java.name) as ch.qos.logback.classic.Logger
        logger.addAppender(logWatcher)
    }

}