package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import com.github.tomakehurst.wiremock.WireMockServer
import io.javalin.Javalin
import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SamtykkeTest {
    private val mockOAuth2Server = MockOAuth2Server()
    private val repository = opprettTestRepositoryMedLokalPostgres()
    private lateinit var javalin: Javalin
    val httpClient = HttpClient.newHttpClient()
    private val wiremockServer = WireMockServer()

    @BeforeAll
    fun init() {
        mockOAuth2Server.start(port = 18301)
        wiremockServer.start()
        val envs = envs(wiremockServer.port())
        javalin = opprettJavalinMedTilgangskontrollForTest(issuerProperties, envs)
        startLocalApplication(javalin = javalin, repository = repository)
    }

    @AfterAll
    fun cleanUp() {
        mockOAuth2Server.shutdown()
        javalin.stop()
    }

    @Test
    fun `Skal returnere 200 OK hvis du har samtykket`() {
        // TODO: Lagre samtykke i databasen
        // TODO: Send med token
        val request = HttpRequest.newBuilder(URI("http://localhost:9000/samtykke")).build()
        val respons = httpClient.send(request, BodyHandlers.ofString())

        assertThat(respons.statusCode()).isEqualTo(200)
    }

    @Test
    fun `Skal returnere 403 OK hvis du ikke har samtykket`() {
        val fødselsnummerInnloggetBruker = tilfeldigFødselsnummer()
        val accessToken = hentToken(mockOAuth2Server, fødselsnummerInnloggetBruker)

        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "111111111"),
        )
        stubHentingAvOrganisasjonerFraAltinnProxy(wiremockServer, organisasjoner)

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/samtykke"))
            .header("Authorization", "Bearer $accessToken")
            .build()
        val respons = httpClient.send(request, BodyHandlers.ofString())
        assertThat(respons.statusCode()).isEqualTo(403)
    }

    @Test
    fun `Skal lagre samtykke på innlogget bruker`() {

    }
}
