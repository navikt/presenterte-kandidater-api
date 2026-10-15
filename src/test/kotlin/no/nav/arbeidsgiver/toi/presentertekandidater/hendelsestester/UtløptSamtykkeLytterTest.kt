package no.nav.arbeidsgiver.toi.presentertekandidater.hendelsestester

import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UtløptSamtykkeLytterTest {
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
    fun `test at kandidat slettes fra kandidatliste når samtykke til deling av cv utløper`() {
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
        val meldingOmTrukketSamtykke =
            trekkSamtykkeTilDelingAvCV(aktørId = aktørId, stillingsId.toString())
        testRapid.sendTestMessage(meldingOmTrukketSamtykke)

        val kandidat = repository.hentKandidat(aktørId, kandidatListe.id!!)
        assertThat(kandidat).isNull()
    }

    private fun trekkSamtykkeTilDelingAvCV(aktørId: String, stillingsId: String, sluttkvittering: Boolean = false) = """
        {
          "aktørId": "$aktørId",
          "fnr": "${aktørId.reversed()}",
          "stillingsTittel": "jeje",
          "utloptTidspunkt": "${ZonedDateTime.now()}",
          "stillingsId": "$stillingsId",
          "@event_name": "samtykke-til-deling-av-cv-utlopt"
          ${if (!sluttkvittering) "" else """, "@slutt_av_hendelseskjede": $sluttkvittering"""}
        }
    """.trimIndent()

    @Test
    fun `Ved mottak av slettethendelse der slutt_av_hendelseskjede ikke er satt skal dette settes og republiseres`() {
        val melding =
            trekkSamtykkeTilDelingAvCV(aktørId = "enAktørId", stillingsId = UUID.randomUUID().toString())

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
            trekkSamtykkeTilDelingAvCV(aktørId = aktørId, stillingsId.toString(), sluttkvittering = true)
        testRapid.sendTestMessage(meldingOmSlettFraArbeidsgiversKandidatliste)

        val kandidat = repository.hentKandidat(aktørId, kandidatListe.id!!)
        assertThat(kandidat).isNotNull

        assertThat(testRapid.inspektør.size).isEqualTo(0)
    }
}