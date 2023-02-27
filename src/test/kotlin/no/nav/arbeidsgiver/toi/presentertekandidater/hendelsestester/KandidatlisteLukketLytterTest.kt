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
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KandidatlisteLukketLytterTest {
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

    @Test
    fun `Behandling av LukketKandidatliste-melding skal være idempotent`() {
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
        testRapid.sendTestMessage(kandidatlisteLukket(stillingsId, organisasjonsnummer))

        val oppdatertKandidatliste = repository.hentKandidatliste(stillingsId)
        assertThat(oppdatertKandidatliste!!.status).isEqualTo(Kandidatliste.Status.LUKKET)
    }

    @Test
    fun `Skal legge til slutt_av_hendelseskjede satt til true`() {
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

        val meldingPublisertPåRapid = testRapid.inspektør.message(0)
        assertTrue(meldingPublisertPåRapid["@slutt_av_hendelseskjede"].asBoolean())
    }

    @Test
    fun `Ved mottak av slutt_av_hendelseskjede satt til true skal det ikke legges ut ny hendelse på rapid`() {
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

        testRapid.sendTestMessage(kandidatlisteLukket(stillingsId, organisasjonsnummer, sluttkvittering = true))

        assertThat(testRapid.inspektør.size).isEqualTo(0)
    }


    private fun kandidatlisteLukket(
        stillingsId: UUID,
        organisasjonsNummer: String,
        sluttkvittering: Boolean = false
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
          ${if (!sluttkvittering) "" else """, "@slutt_av_hendelseskjede": $sluttkvittering"""}
        }
    """.trimIndent()
}