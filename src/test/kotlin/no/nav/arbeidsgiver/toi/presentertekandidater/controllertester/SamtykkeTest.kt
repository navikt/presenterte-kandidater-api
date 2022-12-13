package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import io.javalin.Javalin
import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers

class SamtykkeTest {
    private val mockOAuth2Server = MockOAuth2Server()
    private val repository = opprettTestRepositoryMedLokalPostgres()
    private lateinit var javalin: Javalin
    val httpClient = HttpClient.newHttpClient()

    @BeforeAll
    fun init() {
        javalin = opprettJavalinMedTilgangskontrollForTest(issuerProperties)
        mockOAuth2Server.start(port = 18301)
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

    }

    @Test
    fun `Skal lagre samtykke på innlogget bruker`() {

    }
}
