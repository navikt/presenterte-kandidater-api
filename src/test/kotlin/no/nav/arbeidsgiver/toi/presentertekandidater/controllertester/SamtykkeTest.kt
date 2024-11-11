package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import junit.framework.TestCase.assertTrue
import no.nav.arbeidsgiver.toi.presentertekandidater.*
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

    @Test
    fun `Skal returnere 200 OK hvis du har samtykket Deprekert`() {
        val fødselsnummer = tilfeldigFødselsnummer()
        repository.lagre(fødselsnummer)

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/samtykke"))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val respons = httpClient.send(request, BodyHandlers.ofString())

        assertThat(respons.statusCode()).isEqualTo(200)
        assertTrue(repository.harSamtykket(fødselsnummer))
    }

    @Test
    fun `Skal returnere 403 hvis du ikke har samtykket Deprekert`() {
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "111111111"),
        )
        stubHentingAvOrganisasjonerFraAltinnProxy(wiremockServer, organisasjoner)

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/samtykke"))
            .header("Authorization", "Bearer ${hentToken(tilfeldigFødselsnummer())}")
            .build()
        val respons = httpClient.send(request, BodyHandlers.ofString())
        assertThat(respons.statusCode()).isEqualTo(403)
    }

    @Test
    fun `Skal lagre samtykke på innlogget bruker`() {
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "111111111"),
        )
        stubHentingAvOrganisasjonerFraAltinnProxy(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        val request = HttpRequest.newBuilder(URI("http://localhost:9000/samtykke"))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val respons = httpClient.send(request, BodyHandlers.ofString())
        assertThat(respons.statusCode()).isEqualTo(200)

        assertTrue(repository.harSamtykket(fødselsnummer))
    }

    @Test
    fun `Skal returnere 200 OK selv om samtykke allerede finnes`() {
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "111111111"),
        )
        stubHentingAvOrganisasjonerFraAltinnProxy(wiremockServer, organisasjoner)
        val fødselsnummer = tilfeldigFødselsnummer()

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/samtykke"))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val respons = httpClient.send(request, BodyHandlers.ofString())
        assertThat(respons.statusCode()).isEqualTo(200)
        assertTrue(repository.harSamtykket(fødselsnummer))

        val request2 = HttpRequest.newBuilder(URI("http://localhost:9000/samtykke"))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val respons2 = httpClient.send(request2, BodyHandlers.ofString())
        assertThat(respons2.statusCode()).isEqualTo(200)
    }

    private fun HttpResponse<String>.assertSamtykkeBody(verdi: Boolean) {
        assertThat(body()).isEqualTo("""{"harSamtykket":$verdi}""")
    }

    @Test
    fun `Skal returnere 200 OK og true hvis du har samtykket`() {
        val fødselsnummer = tilfeldigFødselsnummer()
        repository.lagre(fødselsnummer)

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/hentsamtykke"))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val respons = httpClient.send(request, BodyHandlers.ofString())

        assertThat(respons.statusCode()).isEqualTo(200)
        assertTrue(repository.harSamtykket(fødselsnummer))
        respons.assertSamtykkeBody(true)
    }

    @Test
    fun `Skal returnere 200 OK men false hvis du ikke har samtykket`() {
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "111111111"),
        )
        stubHentingAvOrganisasjonerFraAltinnProxy(wiremockServer, organisasjoner)

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/hentsamtykke"))
            .header("Authorization", "Bearer ${hentToken(tilfeldigFødselsnummer())}")
            .build()
        val respons = httpClient.send(request, BodyHandlers.ofString())
        assertThat(respons.statusCode()).isEqualTo(200)
        respons.assertSamtykkeBody(false)
    }
}
