package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.http.objectMapper


fun hentToken(mockOAuth2Server: MockOAuth2Server, fødselsnummer: String = "01838699827"): String {
    return mockOAuth2Server.issueToken(claims = mapOf("pid" to fødselsnummer)).serialize()
}

fun hentUgyldigToken(mockOAuth2Server: MockOAuth2Server): String {
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

