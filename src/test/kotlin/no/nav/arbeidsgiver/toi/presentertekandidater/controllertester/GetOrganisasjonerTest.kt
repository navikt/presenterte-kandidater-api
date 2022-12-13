package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetOrganisasjonerTest {
    private val mockOAuth2Server = MockOAuth2Server()
    private val fuel = FuelManager()
    private val wiremockServer = hentWiremock()

    @BeforeAll
    fun init() {
        mockOAuth2Server.start(port = 18301)
        startLocalApplication()
    }

    @AfterEach
    fun afterEach() {
        wiremockServer.resetAll()
    }

    @AfterAll
    fun afterAll() {
        mockOAuth2Server.shutdown()
    }

    @Test
    fun `Returnerer 200 og liste over alle organisasjoner der bruker har en rolle`() {
        val fødselsnummerInnloggetBruker = "unikt764398"
        val accessToken = hentToken(mockOAuth2Server, fødselsnummerInnloggetBruker)
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "123456789"),
            Testdata.lagAltinnOrganisasjon("Et Navn", "987654321"),
        )
        stubHentingAvOrganisasjonerFraAltinnProxy(wiremockServer, organisasjoner)

        val (_, respons, result) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()

        Assertions.assertThat(respons.statusCode).isEqualTo(200)
        val organisasjonerFraRespons = result.get()
        Assertions.assertThat(organisasjonerFraRespons).hasSize(2)
        Assertions.assertThat(organisasjoner[0]).isEqualTo(organisasjoner[0])
        Assertions.assertThat(organisasjoner[1]).isEqualTo(organisasjoner[1])
    }

    @Test
    fun `Returnerer 200 og tom liste hvis bruker ikke har rolle i noen organisasjoner`() {
        val organisasjoner = emptyList<AltinnReportee>()
        stubHentingAvOrganisasjonerFraAltinnProxy(wiremockServer, organisasjoner)

        val (_, respons, result) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .responseObject<List<AltinnReportee>>()

        Assertions.assertThat(respons.statusCode).isEqualTo(200)
        val organisasjonerFraRespons = result.get()
        Assertions.assertThat(organisasjonerFraRespons).hasSize(0)
    }


    @Test
    fun `Skal ikke bruke cache i Altinn-klient`() {
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "123456789"),
            Testdata.lagAltinnOrganisasjon("Et Navn", "987654321"),
        )
        stubHentingAvOrganisasjonerFraAltinnProxy(wiremockServer, organisasjoner)
        val accessToken = hentToken(mockOAuth2Server, tilfeldigFødselsnummer())

        val (_, respons1, result1) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()

        Assertions.assertThat(respons1.statusCode).isEqualTo(200)
        val organisasjonerFraRespons1 = result1.get()
        Assertions.assertThat(organisasjonerFraRespons1).hasSize(organisasjoner.size)

        val (_, respons2, result2) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()

        Assertions.assertThat(respons2.statusCode).isEqualTo(200)
        val organisasjonerFraRespons2 = result2.get()
        Assertions.assertThat(organisasjonerFraRespons2).hasSize(organisasjoner.size)

        wiremockServer.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo(tokenXWiremockUrl)))
        wiremockServer.verify(2, WireMock.getRequestedFor(WireMock.urlEqualTo(altinnProxyUrl)))
    }
}
