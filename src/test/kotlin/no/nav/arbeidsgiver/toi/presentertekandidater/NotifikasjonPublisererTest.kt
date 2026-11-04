package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.CvDeltData
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.NotifikasjonPubliserer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.UUID

class NotifikasjonPublisererTest {
    @Test
    fun `Publiserer notifikasjon med fast JSON, datoformat og meldingsnøkkel`() {
        val rapid = TestRapid()
        val stillingsId = UUID.fromString("1827cddd-736c-4926-ad2a-a657a1cefa1d")
        val tidspunkt = ZonedDateTime.parse("2024-01-15T12:30:00+01:00[Europe/Oslo]")
        val notifikasjonsId = "$stillingsId-2024-01-15T12:30+01:00[Europe/Oslo]"
        val publiserer = NotifikasjonPubliserer(rapid)

        publiserer.publiserNotifikasjonForCvDelt(
            tidspunkt = tidspunkt,
            stillingsId = stillingsId,
            organisasjonsnummer = "123456789",
            cvDeltData = CvDeltData(
                utførtAvVeilederFornavn = "Ada",
                utførtAvVeilederEtternavn = "Lovelace",
                arbeidsgiversEpostadresser = listOf("leder@example.no", "hr@example.no"),
                meldingTilArbeidsgiver = "Kandidaten er aktuell.",
            ),
            stillingstittel = "Utvikler"
        )

        assertThat(rapid.inspektør.key(0)).isEqualTo(notifikasjonsId)
        val actual = defaultObjectMapper.readTree(rapid.inspektør.message(0).toString())
        val expected = defaultObjectMapper.readTree(
            """
            {
              "notifikasjonsId": "$notifikasjonsId",
              "stillingsId": "$stillingsId",
              "tidspunktForHendelse": "2024-01-15T12:30:00+01:00",
              "virksomhetsnummer": "123456789",
              "utførtAvVeilederFornavn": "Ada",
              "utførtAvVeilederEtternavn": "Lovelace",
              "arbeidsgiversEpostadresser": ["leder@example.no", "hr@example.no"],
              "meldingTilArbeidsgiver": "Kandidaten er aktuell.",
              "stillingstittel": "Utvikler",
              "@event_name": "notifikasjon.cv-delt"
            }
            """.trimIndent()
        )
        assertThat(actual).isEqualTo(expected)
    }
}
