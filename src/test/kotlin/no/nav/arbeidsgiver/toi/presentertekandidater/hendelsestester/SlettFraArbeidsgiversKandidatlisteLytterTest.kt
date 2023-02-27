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
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlettFraArbeidsgiversKandidatlisteLytterTest {
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
}