package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.nimbusds.jwt.SignedJWT
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.TokendingsKlient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8

class TokendingsKlientTest {
    private val wiremockServer = WireMockServer(wireMockConfig().dynamicPort())
    private lateinit var klient: TokendingsKlient

    @BeforeEach
    fun setUp() {
        wiremockServer.start()
        klient = TokendingsKlient(
            mapOf(
                "TOKEN_X_TOKEN_ENDPOINT" to "${wiremockServer.baseUrl()}/token",
                "TOKEN_X_PRIVATE_JWK" to Testdata.privateJwk,
                "TOKEN_X_CLIENT_ID" to "test-client",
                "TOKEN_X_ISSUER" to "test-issuer"
            )
        )
    }

    @AfterEach
    fun tearDown() {
        wiremockServer.stop()
    }

    @Test
    fun `Veksling returnerer access_token og godtar ukjente felt i responsen`() {
        wiremockServer.stubFor(
            post("/token").willReturn(
                okJson("""{"access_token":"utvekslet-testtoken","expires_in":120,"ukjent":"verdi"}""")
            )
        )

        val token = klient.veksleInnToken("innkommende-testtoken", "test:audience")

        assertThat(token).isEqualTo("utvekslet-testtoken")
    }

    @Test
    fun `Veksling sender POST med seks korrekt kodede form-felt`() {
        wiremockServer.stubFor(
            post("/token").willReturn(okJson("""{"access_token":"testtoken","expires_in":120}"""))
        )
        val accessToken = "testtoken+&=ø"
        val audience = "test:audience+&=ø"

        klient.veksleInnToken(accessToken, audience)

        wiremockServer.verify(
            1, postRequestedFor(urlEqualTo("/token"))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
        )
        val request = wiremockServer.findAll(postRequestedFor(urlEqualTo("/token"))).single()
        val felt = request.bodyAsString.split("&").map { parameter ->
            val deler = parameter.split("=", limit = 2)
            URLDecoder.decode(deler[0], UTF_8) to URLDecoder.decode(deler[1], UTF_8)
        }
        assertThat(felt).hasSize(6)
        val formData = felt.toMap()
        assertThat(formData.keys).containsExactlyInAnyOrder(
            "grant_type", "client_assertion", "client_assertion_type",
            "subject_token_type", "audience", "subject_token"
        )
        assertThat(formData["grant_type"]).isEqualTo("urn:ietf:params:oauth:grant-type:token-exchange")
        assertThat(formData["client_assertion_type"]).isEqualTo("urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
        assertThat(formData["subject_token_type"]).isEqualTo("urn:ietf:params:oauth:token-type:jwt")
        assertThat(formData["audience"]).isEqualTo(audience)
        assertThat(formData["subject_token"]).isEqualTo(accessToken)
        val assertion = SignedJWT.parse(formData.getValue("client_assertion"))
        assertThat(assertion.jwtClaimsSet.subject).isEqualTo("test-client")
        assertThat(assertion.jwtClaimsSet.issuer).isEqualTo("test-client")
        assertThat(assertion.jwtClaimsSet.audience).containsExactly("${wiremockServer.baseUrl()}/token")
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 500])
    fun `HTTP-feil gir RuntimeException uten token eller respons-body i feilmeldingen`(statuskode: Int) {
        val accessToken = "innkommende-testtoken"
        val responseBody = """{"error":"feil-med-responsmarkor","access_token":"utvekslet-testtoken"}"""
        wiremockServer.stubFor(
            post("/token").willReturn(aResponse().withStatus(statuskode).withBody(responseBody))
        )

        val exception = assertThrows<RuntimeException> {
            klient.veksleInnToken(accessToken, "test:audience")
        }

        assertThat(exception.message)
            .isEqualTo("Kunne ikke veksle inn token hos TokenX")
            .doesNotContain(accessToken, "utvekslet-testtoken", responseBody)
    }

    @Test
    fun `Ugyldig JSON gir RuntimeException uten token eller respons-body i feilmeldingen`() {
        val accessToken = "innkommende-testtoken"
        val responseBody = "ugyldig-json-med-utvekslet-testtoken"
        wiremockServer.stubFor(post("/token").willReturn(ok(responseBody)))

        val exception = assertThrows<RuntimeException> {
            klient.veksleInnToken(accessToken, "test:audience")
        }

        assertThat(exception.message)
            .isEqualTo("Kunne ikke veksle inn token hos TokenX")
            .doesNotContain(accessToken, responseBody)
    }
}
