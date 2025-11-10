package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnTilgang
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnTilgangerResponse
import no.nav.arbeidsgiver.toi.presentertekandidater.samtykke.SamtykkeRepository
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.http.objectMapper
import kotlin.random.Random

private var harStartetMockOAuth2Server = false
val mockOAuth2Server = MockOAuth2Server()

fun startMockOAuth2Server() {
    if (!harStartetMockOAuth2Server) {
        mockOAuth2Server.start(port = 18301)
        harStartetMockOAuth2Server = true
    }
}

fun tilfeldigFødselsnummer(): String {
    fun Int.tilStrengMedToTegn() = this.toString().let { if (it.length == 1) "0$it" else it }
    fun Int.tilÅrstallMedToTegn() = this.toString().let { it.substring(2) }
    val tilfeldigDag = Random.nextInt(32).tilStrengMedToTegn()
    val tilfeldigMåned = Random.nextInt(13).tilStrengMedToTegn()
    val tilfeldigÅr = Random.nextInt(1910, 2010).tilÅrstallMedToTegn()
    val tilfeldigPersonnummer = Random.nextInt(10000, 90000)
    return "$tilfeldigDag$tilfeldigMåned$tilfeldigÅr$tilfeldigPersonnummer"
}

fun tilfeldigVirksomhetsnummer() = tilfeldigFødselsnummer().substring(0, 9)

fun lagreSamtykke(fødselsnummer: String) {
    SamtykkeRepository(dataSource).lagre(fødselsnummer)
}

fun hentToken(fødselsnummer: String): String {
    return mockOAuth2Server.issueToken(
        audience = "clientId",
        claims = mapOf("pid" to fødselsnummer)
    ).serialize()
}

fun hentUgyldigToken(): String {
    return mockOAuth2Server.issueToken(issuerId = "feilissuer").serialize()
}

const val tokenXWiremockUrl = "/token-x-token-endpoint"

const val altinnProxyUrl = "/altinn-proxy-url"

//TODO: Denne er nå prikk lik som stubHentingAvtilgangerFraAltinnProxyFiltrertPåRekruttering. Enten slå dem sammen, eller endre oppførselen
fun stubHentingAvTilgangerFraAltinnProxy(wiremockServer: WireMockServer, altinnTilganger: List<AltinnTilgang>) {
    val exchangeToken = "exchangeToken"
    stubVekslingAvTokenX(wiremockServer, exchangeToken)

    val altinnTilgangerResponse = AltinnTilgangerResponse(
        isError = false,
        hierarki = altinnTilganger,
        orgNrTilTilganger = altinnTilganger.associate {
            it.orgnr to listOf(it.altinn3Tilganger, it.altinn2Tilganger).flatten().toSet()
        },
        tilgangTilOrgNr = altinnTilganger.flatMap { tilgang ->
            listOf(
                tilgang.altinn2Tilganger to tilgang.orgnr,
                tilgang.altinn3Tilganger to tilgang.orgnr
            )
        }.flatMap { (tilganger, orgnr) ->
            tilganger.map { it to orgnr }
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
    )

    val organisasjonerJson = objectMapper.writeValueAsString(altinnTilgangerResponse)
    wiremockServer.stubFor(
        WireMock.post(altinnProxyUrl)
            .withHeader("Authorization", WireMock.containing("Bearer $exchangeToken"))
            .willReturn(
                WireMock.ok(organisasjonerJson)
                    .withHeader("Content-Type", "application/json")
            )
    )
}

fun stubHentingAvTilgangerFraAltinnProxyFiltrertPåRekruttering(
    wiremockServer: WireMockServer,
    altinnTilganger: List<AltinnTilgang>
) {
    val exchangeToken = "exchangeToken"
    stubVekslingAvTokenX(wiremockServer, exchangeToken)

    val altinnTilgangerResponse = AltinnTilgangerResponse(
        isError = false,
        hierarki = altinnTilganger,
        orgNrTilTilganger = altinnTilganger.associate {
            it.orgnr to listOf(it.altinn3Tilganger, it.altinn2Tilganger).flatten().toSet()
        },
        tilgangTilOrgNr = altinnTilganger.flatMap { tilgang ->
            fun flatUtTilganger(tilgang: AltinnTilgang): List<Pair<List<String>, String>> {
                val nåværendeTilgang = listOf(
                    tilgang.altinn2Tilganger.toList() to tilgang.orgnr,
                    tilgang.altinn3Tilganger.toList() to tilgang.orgnr
                )
                val underenhetTilganger = tilgang.underenheter.flatMap { flatUtTilganger(it) }
                return nåværendeTilgang + underenhetTilganger
            }
            flatUtTilganger(tilgang)
        }.flatMap { (tilganger, orgnr) ->
            tilganger.map { it to orgnr }
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
    )

    val organisasjonerJson = objectMapper.writeValueAsString(altinnTilgangerResponse)
    wiremockServer.stubFor(
        WireMock.post(altinnProxyUrl)
            .withHeader("Authorization", WireMock.containing("Bearer $exchangeToken"))
            .willReturn(
                WireMock.ok(organisasjonerJson)
                    .withHeader("Content-Type", "application/json")
            )
    )
}

fun stubHttpStatus500FraAltinnProxy(wiremockServer: WireMockServer) {
    val exchangeToken = "exchangeToken"
    stubVekslingAvTokenX(wiremockServer, exchangeToken)

    wiremockServer.stubFor(
        WireMock.get(altinnProxyUrl)
            .withHeader("Authorization", WireMock.containing("Bearer $exchangeToken"))
            .willReturn(
                WireMock.serverError()
                    .withStatus(500)
                    .withStatusMessage("Noe gikk galt")
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
