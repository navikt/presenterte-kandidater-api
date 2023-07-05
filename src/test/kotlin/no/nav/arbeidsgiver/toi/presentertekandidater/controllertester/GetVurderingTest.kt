package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import uk.org.webcompere.modelassert.json.JsonAssertions.assertJson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZonedDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetVurderingTest {
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    val httpClient = HttpClient.newHttpClient()
    private val mockOAuth2Server = MockOAuth2Server()

    @BeforeAll
    fun init() {
        mockOAuth2Server.start(port = 18300)
        startLocalApplication()
    }

    @AfterAll
    fun teardown() {
        mockOAuth2Server.shutdown()
    }

    @Test
    fun `Skal oppdatere arbeidsgivers vurdering og returnere 200 OK`() {
        val stillingId = settOppKandidatlisteMedToKandidater()

        //val fødselsnummer = tilfeldigFødselsnummer()
        //lagreSamtykke(fødselsnummer)
        val accessToken = hentVeilederToken("A000000")

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidatliste/${stillingId}/vurdering"))
            .header("Authorization", "Bearer $accessToken")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)
        assertJson(response.body()).isEqualTo("""
            [
                {
                    "aktørId": "3498943",
                    "vurdering": "TIL_VURDERING"
                },
                {
                    "aktørId": "54321",
                    "vurdering": "FÅTT_JOBBEN"
                }
            ]
        """.trimIndent())
    }

    @Test
    fun `Gir 401 hvis token mangler`() {
        val stillingId = settOppKandidatlisteMedToKandidater()
        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidatliste/${stillingId}/vurdering"))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(401)
    }

    @Test
    fun `Gir 403 hvis ikke veileder-token`() {
        val stillingId = settOppKandidatlisteMedToKandidater()
        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidatliste/${stillingId}/vurdering"))
            .header("Authorization", "Bearer ${mockOAuth2Server.issueToken().serialize()}")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `To kall returnerer samme resultatet`() {
        val stillingId = settOppKandidatlisteMedToKandidater()

        val accessToken = hentVeilederToken("A000000")

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidatliste/${stillingId}/vurdering"))
            .header("Authorization", "Bearer $accessToken")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val response2 = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(response2.statusCode())
        assertJson(response.body()).isEqualTo(response2.body())
    }

    private fun settOppKandidatlisteMedToKandidater(): UUID? {
        val stillingId = UUID.randomUUID()
        val virksomhetsnummer = "174379426"
        repository.lagre(Testdata.kandidatliste().copy(stillingId = stillingId, virksomhetsnummer = virksomhetsnummer))
        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat = Kandidat(
            aktørId = "3498943",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = ZonedDateTime.now().minusDays(1)
        )
        repository.lagre(kandidat)
        repository.lagre(
            kandidat.copy(
                aktørId = "54321",
                arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.FÅTT_JOBBEN,
                uuid = UUID.randomUUID()
            )
        )
        return stillingId
    }

    fun hentVeilederToken(navIdent: String) = mockOAuth2Server.issueToken(
        claims = mapOf(
            "NAVident" to navIdent
        )
    ).serialize()
}