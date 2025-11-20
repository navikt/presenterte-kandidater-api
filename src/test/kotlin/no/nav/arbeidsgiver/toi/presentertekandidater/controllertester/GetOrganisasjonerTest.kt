package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnReportee
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnTilgang
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetOrganisasjonerTest {
    private val fuel = FuelManager()
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
            Testdata.lagAltinnTilgangUtenRekrutteringsrettighet("Et Navn", "123456789"),
            Testdata.lagAltinnTilgangUtenRekrutteringsrettighet("Et Navn", "987654321")
        )

        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val (_, respons, result) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(hentToken(tilfeldigFødselsnummer()))
            .responseObject<List<AltinnReportee>>()

        Assertions.assertThat(respons.statusCode).isEqualTo(200)
        val organisasjonerFraRespons = result.get()
        Assertions.assertThat(organisasjonerFraRespons).hasSize(2)
        Assertions.assertThat(organisasjoner[0]).isEqualTo(organisasjoner[0])
        Assertions.assertThat(organisasjoner[1]).isEqualTo(organisasjoner[1])
    }

    @Test
    fun `Returnerer 200 og tom liste hvis bruker ikke har rolle i noen organisasjoner`() {
        val organisasjoner = emptyList<AltinnTilgang>()
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val (_, respons, result) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(hentToken(tilfeldigFødselsnummer()))
            .responseObject<List<AltinnReportee>>()

        Assertions.assertThat(respons.statusCode).isEqualTo(200)
        val organisasjonerFraRespons = result.get()
        Assertions.assertThat(organisasjonerFraRespons).hasSize(0)
    }


    @Test
    fun `Skal bruke cache i Altinn-klient`() {
        val organisasjoner = listOf(
            Testdata.lagAltinnTilgangUtenRekrutteringsrettighet("Et Navn", "123456789"),
            Testdata.lagAltinnTilgangUtenRekrutteringsrettighet("Et Navn", "987654321"),
        )
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        val (_, respons1, result1) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(hentToken(fødselsnummer))
            .responseObject<List<AltinnReportee>>()

        Assertions.assertThat(respons1.statusCode).isEqualTo(200)
        val organisasjonerFraRespons1 = result1.get()
        Assertions.assertThat(organisasjonerFraRespons1).hasSize(organisasjoner.size)

        val (_, respons2, result2) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(hentToken(fødselsnummer))
            .responseObject<List<AltinnReportee>>()

        Assertions.assertThat(respons2.statusCode).isEqualTo(200)
        val organisasjonerFraRespons2 = result2.get()
        Assertions.assertThat(organisasjonerFraRespons2).hasSize(organisasjoner.size)

        wiremockServer.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo(tokenXWiremockUrl)))
        wiremockServer.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo(altinnProxyUrl)))
    }
}
