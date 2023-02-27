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
class SlettetStillingLytterTest {
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
    fun `test at annullert kandidatliste registreres som slettet fra databasen`() {
        val stillingsId = UUID.randomUUID()
        val aktørId = "1122334455"
        val organisasjonsnummer = "123"
        var kandidatListe = repository.lagre(Kandidatliste.ny(stillingsId, "en stilling", organisasjonsnummer))
        repository.lagre(
            Kandidat(
                uuid = UUID.randomUUID(),
                aktørId = aktørId,
                kandidatlisteId = kandidatListe.id!!,
                arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING
            )
        )

        val meldingOmKandidatlisteLukket = slettetStillingOgKandidatlisteMelding(kandidatListe.uuid.toString(), stillingsId.toString())
        testRapid.sendTestMessage(meldingOmKandidatlisteLukket)

        kandidatListe = repository.hentKandidatliste(stillingsId)!!
        assertThat(kandidatListe.slettet).isTrue
    }


    private fun slettetStillingOgKandidatlisteMelding(kandidatlisteId: String, stillingsId: String, sluttkvittering: Boolean = false) = """
        {
          "organisasjonsnummer": "312113341",
          "kandidatlisteId": "$kandidatlisteId",
          "tidspunkt": "2023-02-09T09:46:01.027221527",
          "stillingsId": "$stillingsId",
          "utførtAvNavIdent": "Z994633",
          "@event_name": "kandidat_v2.SlettetStillingOgKandidatliste",
          "@id": "74b0b8dd-315f-406f-9979-e0bec5bcc5b6",
          "@opprettet": "2023-02-09T09:46:01.027221527"
          ${if (!sluttkvittering) "" else """, "@slutt_av_hendelseskjede": $sluttkvittering"""}
        }
    """.trimIndent()

    @Test
    fun `Ved mottak av slettethendelse der slutt_av_hendelseskjede ikke er satt skal dette settes og republiseres`() {
        val melding =
            slettetStillingOgKandidatlisteMelding(kandidatlisteId = UUID.randomUUID().toString(), stillingsId = UUID.randomUUID().toString())

        testRapid.sendTestMessage(melding)

        assertThat(testRapid.inspektør.size).isEqualTo(1)
        assertThat(testRapid.inspektør.message(0)["@slutt_av_hendelseskjede"].asBoolean()).isTrue
    }

    @Test
    fun `Skal ikke behandle meldinger med slutt_av_hendelseskjede satt til true`() {
        val stillingsId = UUID.randomUUID()
        val aktørId = "1122334455"
        val organisasjonsnummer = "123"
        var kandidatListe = repository.lagre(Kandidatliste.ny(stillingsId, "en stilling", organisasjonsnummer))
        repository.lagre(
            Kandidat(
                uuid = UUID.randomUUID(),
                aktørId = aktørId,
                kandidatlisteId = kandidatListe.id!!,
                arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING
            )
        )
        val melding =
            slettetStillingOgKandidatlisteMelding(kandidatlisteId = UUID.randomUUID().toString(), stillingsId = stillingsId.toString(), sluttkvittering = true)

        testRapid.sendTestMessage(melding)

        kandidatListe = repository.hentKandidatliste(stillingsId)!!
        assertThat(kandidatListe.slettet).isFalse

        assertThat(testRapid.inspektør.size).isEqualTo(0)
    }
}