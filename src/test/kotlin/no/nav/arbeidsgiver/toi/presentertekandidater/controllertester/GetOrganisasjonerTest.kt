package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnReportee
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnTilgang
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetOrganisasjonerTest {
    private val httpClient = HttpClient.newHttpClient()
    private val wiremockServer = hentWiremock()

    @BeforeAll
    fun init() {
        startLocalApplication()
    }

    @AfterEach
    fun afterEach() {
        wiremockServer.resetAll()
    }

    @Test
    fun `Returnerer 200 og liste over alle organisasjoner der bruker har en rolle`() {
        val organisasjoner = listOf(
            Testdata.lagAltinnTilgangUtenRettighetKandidater("Et Navn", "123456789"),
            Testdata.lagAltinnTilgangUtenRettighetKandidater("Et Navn", "987654321")
        )

        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/organisasjoner"))
            .header("Authorization", "Bearer ${hentToken(tilfeldigFødselsnummer())}")
            .build()
        val respons = httpClient.send(request, BodyHandlers.ofString())

        Assertions.assertThat(respons.statusCode()).isEqualTo(200)
        val organisasjonerFraRespons = defaultObjectMapper.readValue<List<AltinnReportee>>(respons.body())
        Assertions.assertThat(organisasjonerFraRespons).hasSize(2)
        Assertions.assertThat(organisasjoner[0]).isEqualTo(organisasjoner[0])
        Assertions.assertThat(organisasjoner[1]).isEqualTo(organisasjoner[1])
    }

    @Test
    fun `Returnerer 200 og tom liste hvis bruker ikke har rolle i noen organisasjoner`() {
        val organisasjoner = emptyList<AltinnTilgang>()
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/organisasjoner"))
            .header("Authorization", "Bearer ${hentToken(tilfeldigFødselsnummer())}")
            .build()
        val respons = httpClient.send(request, BodyHandlers.ofString())

        Assertions.assertThat(respons.statusCode()).isEqualTo(200)
        val organisasjonerFraRespons = defaultObjectMapper.readValue<List<AltinnReportee>>(respons.body())
        Assertions.assertThat(organisasjonerFraRespons).hasSize(0)
    }


    @Test
    fun `Skal bruke cache i Altinn-klient`() {
        val organisasjoner = listOf(
            Testdata.lagAltinnTilgangUtenRettighetKandidater("Et Navn", "123456789"),
            Testdata.lagAltinnTilgangUtenRettighetKandidater("Et Navn", "987654321"),
        )
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        val request1 = HttpRequest.newBuilder(URI("http://localhost:9000/organisasjoner"))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val respons1 = httpClient.send(request1, BodyHandlers.ofString())

        Assertions.assertThat(respons1.statusCode()).isEqualTo(200)
        val organisasjonerFraRespons1 = defaultObjectMapper.readValue<List<AltinnReportee>>(respons1.body())
        Assertions.assertThat(organisasjonerFraRespons1).hasSize(organisasjoner.size)

        val request2 = HttpRequest.newBuilder(URI("http://localhost:9000/organisasjoner"))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val respons2 = httpClient.send(request2, BodyHandlers.ofString())

        Assertions.assertThat(respons2.statusCode()).isEqualTo(200)
        val organisasjonerFraRespons2 = defaultObjectMapper.readValue<List<AltinnReportee>>(respons2.body())
        Assertions.assertThat(organisasjonerFraRespons2).hasSize(organisasjoner.size)

        wiremockServer.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo(tokenXWiremockUrl)))
        wiremockServer.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo(altinnProxyUrl)))
    }
}
