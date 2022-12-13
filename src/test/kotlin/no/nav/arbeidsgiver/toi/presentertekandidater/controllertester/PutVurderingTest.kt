package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester


import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.time.ZonedDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PutVurderingTest {
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    private val wiremockServer = hentWiremock()
    val httpClient = HttpClient.newHttpClient()

    @BeforeAll
    fun init() {
        startLocalApplication()
    }

    @Test
    fun `Skal oppdatere arbeidsgivers vurdering og returnere 200 OK`() {
        val stillingId = UUID.randomUUID()
        val virksomhetsnummer = "174379426"
        repository.lagre(kandidatliste().copy(stillingId = stillingId, virksomhetsnummer = virksomhetsnummer))
        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat = Kandidat(
            aktørId = "3498943",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = ZonedDateTime.now().minusDays(1)
        )
        repository.lagre(kandidat)
        val organisasjoner = listOf(Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummer))
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

        val accessToken = hentToken(mockOAuth2Server, tilfeldigFødselsnummer())
        val body = """
            {
              "arbeidsgiversVurdering": "IKKE_AKTUELL"
            }
        """.trimIndent()

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidat/${kandidat.uuid}/vurdering"))
            .header("Authorization", "Bearer $accessToken")
            .PUT(BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)
        val kandidatFraDatabasen = repository.hentKandidat(kandidat.aktørId, kandidatliste.id!!)
        assertThat(kandidatFraDatabasen!!.arbeidsgiversVurdering).isEqualTo(Kandidat.ArbeidsgiversVurdering.IKKE_AKTUELL)
        assertThat(kandidatFraDatabasen.sistEndret).isEqualToIgnoringSeconds(ZonedDateTime.now())
    }

    @Test
    fun `Kall med nullverdi i vurderingsfeltet skal returnere 400`() {
        val stillingId = UUID.randomUUID()
        val virksomhetsnummer = "23569876"
        repository.lagre(kandidatliste().copy(stillingId = stillingId, virksomhetsnummer = virksomhetsnummer))
        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat = Kandidat(
            aktørId = "1234",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = ZonedDateTime.now().minusDays(1)
        )
        repository.lagre(kandidat)
        val organisasjoner = listOf(Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummer))
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

        val body = """
            {
              "arbeidsgiversVurdering": null
            }
        """.trimIndent()

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidat/${kandidat.uuid}/vurdering"))
            .header("Authorization", "Bearer ${hentToken(mockOAuth2Server, tilfeldigFødselsnummer())}")
            .PUT(BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(400)
        val kandidatFraDatabasen = repository.hentKandidat(kandidat.aktørId, kandidatliste.id!!)
        assertThat(kandidatFraDatabasen!!.arbeidsgiversVurdering).isEqualTo(kandidat.arbeidsgiversVurdering)
        assertThat(kandidatFraDatabasen.sistEndret).isEqualToIgnoringNanos(kandidat.sistEndret)
    }

    @Test
    fun `Kall med ukjent verdi i vurderingsfeltet skal returnere 400`() {
        val organisasjoner = listOf(Testdata.lagAltinnOrganisasjon("Et Navn", "53987549"))
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)
        val body = """
            {
              "arbeidsgiversVurdering": "NY"
            }
        """.trimIndent()

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidat/${UUID.randomUUID()}/vurdering"))
            .header("Authorization", "Bearer ${hentToken(mockOAuth2Server, tilfeldigFødselsnummer())}")
            .PUT(BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `Gir 400 hvis kandidat ikke eksisterer`() {
        val organisasjoner = listOf(Testdata.lagAltinnOrganisasjon("Et Navn", "221133445"))
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)
        val body = """
            {
              "arbeidsgiversVurdering": "FÅTT_JOBBEN"
            }
        """.trimIndent()

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidat/${UUID.randomUUID()}/vurdering"))
            .header("Authorization", "Bearer ${hentToken(mockOAuth2Server, tilfeldigFødselsnummer())}")
            .PUT(BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `Gir 403 hvis bruker ikke representerer virksomheten`() {
        val kandidatlistasVirksomhetsnummer = "543219876"
        val innloggetBrukersVirksomhetsnummer = "987654321"
        val stillingId = UUID.randomUUID()
        repository.lagre(kandidatliste().copy(stillingId = stillingId, virksomhetsnummer = kandidatlistasVirksomhetsnummer))
        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat = Kandidat(
            aktørId = "1234",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = ZonedDateTime.now().minusDays(1)
        )
        repository.lagre(kandidat)
        val organisasjoner = listOf(Testdata.lagAltinnOrganisasjon("Et Navn", innloggetBrukersVirksomhetsnummer),)
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)
        val body = """
            {
              "arbeidsgiversVurdering": "FÅTT_JOBBEN"
            }
        """.trimIndent()

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidat/${kandidat.uuid}/vurdering"))
            .header("Authorization", "Bearer ${hentToken(mockOAuth2Server, tilfeldigFødselsnummer())}")
            .PUT(BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(403)
    }
}
