package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.flereKandidaterFraES
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.ZonedDateTime
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenSearchKlientTest {

    private val wiremockServer = WireMockServer(9999)
    private lateinit var openSearchKlient: OpenSearchKlient

    @BeforeAll
    fun setUp() {
        wiremockServer.start()
        val miljøvariabler = mapOf(
            "OPEN_SEARCH_URI" to "http://localhost:${wiremockServer.port()}",
            "OPEN_SEARCH_USERNAME" to "gunnar",
            "OPEN_SEARCH_PASSWORD" to "xyz"
        )
        openSearchKlient = OpenSearchKlient(miljøvariabler)

    }

    @Test
    fun `Skal mappe respons fra OpenSearch korrekt når vi henter kandidater`() {
        val aktørIder = listOf("12345", "67890")
        val openSearchRequestBody = openSearchKlient.lagBodyForHentingAvCver(aktørIder)
        val openSearchResponseBody = flereKandidaterFraES(aktørIder[0], aktørIder[1])
        stubHentingAvKandidater(requestBody = openSearchRequestBody, responsBody = openSearchResponseBody)

        val kandidater = openSearchKlient.hentCver(aktørIder)

        assertThat(kandidater).hasSize(2)

        /*
        assertNotNull(kandidat)
        assertThat(kandidat.aktørId).isEqualTo(aktørId)
        assertThat(kandidat.fornavn).isEqualTo(fornavn)
        assertThat(kandidat.etternavn).isEqualTo(etternavn)
        assertThat(kandidat.bosted).isEqualTo("Svolvær")
        assertThat(kandidat.epost).isEqualTo("hei@hei.no")
        assertThat(kandidat.mobiltelefonnummer).isEqualTo("99887766")
        assertThat(kandidat.alder).isEqualTo(40)
        assertThat(kandidat.kompetanse).containsExactlyInAnyOrder("Sykepleievitenskap", "Markedsanalyse")
        assertThat(kandidat.arbeidserfaring).containsExactlyInAnyOrder(
            OpensearchData.Arbeidserfaring(
                fraDato = ZonedDateTime.parse("2018-06-30T22:00:00.000+00:00[UTC]"),
                tilDato = ZonedDateTime.parse("2022-04-30T22:00:00.000+00:00[UTC]"),
                arbeidsgiver = "Stormote AS",
                sted = "Oslo",
                stillingstittel = "Butikkmedarbeider klesbutikk",
                beskrivelse = "Jobb i butikk som drev med både klær og tekstil. Som grossist og til privatkunder."
                ),
            OpensearchData.Arbeidserfaring(
                fraDato = ZonedDateTime.parse("2015-04-30T22:00:00.000+00:00[UTC]"),
                tilDato = ZonedDateTime.parse("2018-02-28T23:00:00.000+00:00[UTC]"),
                arbeidsgiver = "H&M Storo",
                sted = "Oslo",
                stillingstittel = "Butikkmedarbeider klesbutikk",
                beskrivelse = "Ordinær ansatt i klesbutikk. Hadde behov for å trappe ned etter en stressende stilling som daglig leder hos Carlings. Generelt butikkarbeid, salg."
            )
        )
        assertThat(kandidat.ønsketYrke).containsExactlyInAnyOrder("Kokkelærling", "Skipskokk")
        assertThat(kandidat.sammendrag).isEqualTo("Dette er et sammendrag.")
        assertThat(kandidat.utdanning).containsExactlyInAnyOrder(
            OpensearchData.Utdanning(
                fra = ZonedDateTime.parse("2019-07-31T22:00:00.000+00:00[UTC]"),
                til = ZonedDateTime.parse("2021-05-31T22:00:00.000+00:00[UTC]"),
                utdanningsretning = "Master markedsføring",
                utdannelsessted = "NHH",
                beskrivelse = "Markedsføring",
            ),
            OpensearchData.Utdanning(
                fra = ZonedDateTime.parse("1997-05-31T22:00:00.000+00:00[UTC]"),
                til = ZonedDateTime.parse("2000-05-31T22:00:00.000+00:00[UTC]"),
                utdanningsretning = "Bachelor i Sykepleie",
                utdannelsessted = "Lovisenberg høyskole",
                beskrivelse = "Sykepleie",
            )
        )
        assertThat(kandidat.språk).containsExactlyInAnyOrder(
            OpensearchData.Språk(
                navn = "Engelsk",
                muntlig = "VELDIG_GODT",
                skriftlig = "GODT"
            ),
            OpensearchData.Språk(
                navn = "Norsk",
                muntlig = "FOERSTESPRAAK",
                skriftlig = "FOERSTESPRAAK"
            )
        )
         */
    }

    @Test
    fun `hentKandiat skal returnere null når kandidat ikke finnes`() {
        val aktørIder = listOf("12345", "67890")
        val openSearchRequestBody = openSearchKlient.lagBodyForHentingAvCver(aktørIder)
        val openSearchResponseBody = Testdata.ingenTreffKandidatOpensearchJson
        stubHentingAvKandidater(requestBody = openSearchRequestBody, responsBody = openSearchResponseBody)

        val kandidater = openSearchKlient.hentCver(aktørIder)

        assertThat(kandidater).hasSize(0)
    }


    fun stubHentingAvKandidater(requestBody: String, responsBody: String) {
        wiremockServer.stubFor(get("/veilederkandidat_current/_search/")
            .withBasicAuth("gunnar", "xyz")
            .withRequestBody(containing(requestBody))
            .willReturn(ok(responsBody)))
    }
 }