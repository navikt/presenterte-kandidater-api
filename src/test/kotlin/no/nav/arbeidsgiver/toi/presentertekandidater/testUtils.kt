package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.http.objectMapper
import kotlin.random.Random

val mockOAuth2Server = MockOAuth2Server().also { it.start(port = 18301) }

private fun tilfeldigFødselsnummer(): String {
    fun Int.tilStrengMedToTegn() = this.toString().let { if (it.length == 1) "0$it" else it }
    val tilfeldigDag = Random.nextInt(32).tilStrengMedToTegn()
    val tilfeldigMåned = Random.nextInt(13).tilStrengMedToTegn()
    val tilfeldigÅr = Random.nextInt(1910, 2010).tilStrengMedToTegn()
    val tilfeldigPersonnummer = Random.nextInt(10000, 90000)
    return "$tilfeldigDag$tilfeldigMåned$tilfeldigÅr$tilfeldigPersonnummer"
}

fun hentToken(): String {
    return mockOAuth2Server.issueToken(claims = mapOf("pid" to tilfeldigFødselsnummer())).serialize()
}

fun hentUgyldigToken(): String {
    return mockOAuth2Server.issueToken(issuerId = "feilissuer").serialize()
}

const val tokenXWiremockUrl = "/token-x-token-endpoint"

const val altinnProxyUrl =
    "/altinn-proxy-url/v2/organisasjoner?top=500&skip=0&filter=Type+ne+%27Person%27+and+Status+eq+%27Active%27"

const val altinnProxyUrlFiltrertPåRekruttering =
    "/altinn-proxy-url/v2/organisasjoner?top=500&skip=0&serviceCode=5078&serviceEdition=1&filter=Type+ne+%27Person%27+and+Status+eq+%27Active%27"

fun stubHentingAvOrganisasjonerFraAltinnProxy(wiremockServer: WireMockServer, organisasjoner: List<AltinnReportee>) {
    val exchangeToken = "exchangeToken"
    stubVekslingAvTokenX(wiremockServer, exchangeToken)

    val organisasjonerJson = objectMapper.writeValueAsString(organisasjoner)
    wiremockServer.stubFor(
        WireMock.get(altinnProxyUrl)
            .withHeader("Authorization", WireMock.containing("Bearer $exchangeToken"))
            .willReturn(
                WireMock.ok(organisasjonerJson)
                    .withHeader("Content-Type", "application/json")
            )
    )
}

fun stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer: WireMockServer, organisasjoner: List<AltinnReportee>) {
    val exchangeToken = "exchangeToken"
    stubVekslingAvTokenX(wiremockServer, exchangeToken)

    val organisasjonerJson = objectMapper.writeValueAsString(organisasjoner)
    wiremockServer.stubFor(
        WireMock.get(altinnProxyUrlFiltrertPåRekruttering)
            .withHeader("Authorization", WireMock.containing("Bearer $exchangeToken"))
            .willReturn(
                WireMock.ok(organisasjonerJson)
                    .withHeader("Content-Type", "application/json")
            )
    )
}

private fun stubVekslingAvTokenX(wiremockServer: WireMockServer, token: String) {
    val responseBody = """
            {
                "access_token": "$token",
                "expires_in": 123
            }
        """.trimIndent()

    wiremockServer.stubFor(
        WireMock.post("/token-x-token-endpoint")
            .willReturn(WireMock.ok(responseBody))
    )
}
