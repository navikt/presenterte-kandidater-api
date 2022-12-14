package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import junit.framework.TestCase.assertTrue
import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SamtykkeTest {
    private val httpClient = HttpClient.newHttpClient()
    private val wiremockServer = hentWiremock()
    private val repository = samtykkeRepositoryMedLokalPostgres()

    @BeforeAll
    fun init() {
        startLocalApplication()
    }

    @Disabled
    @Test
    fun `Skal returnere 200 OK hvis du har samtykket`() {
        val fødselsnummer = tilfeldigFødselsnummer()
        repository.lagre(fødselsnummer)

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/samtykke"))
            .header("Authorization", "Bearer ${hentToken()}")
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build()
        val respons = httpClient.send(request, BodyHandlers.ofString())

        assertThat(respons.statusCode()).isEqualTo(200)
        assertTrue(repository.harSamtykket(fødselsnummer))
    }

    @Test
    fun `Skal returnere 403 OK hvis du ikke har samtykket`() {
        val accessToken = hentToken()

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
