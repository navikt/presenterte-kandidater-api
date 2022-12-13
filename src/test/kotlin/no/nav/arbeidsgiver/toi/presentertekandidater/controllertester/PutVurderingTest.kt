package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.tomakehurst.wiremock.WireMockServer
import io.javalin.Javalin
import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.ZonedDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PutVurderingTest {
    private val mockOAuth2Server = MockOAuth2Server()
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    private val fuel = FuelManager()
    private lateinit var javalin: Javalin

    @BeforeAll
    fun init() {
        val envs = envs(wiremockServer.port())
        javalin = opprettJavalinMedTilgangskontrollForTest(issuerProperties, envs)
        mockOAuth2Server.start(port = 18301)
        startLocalApplication(javalin = javalin, envs = envs)
    }

    @BeforeEach
    fun beforeEach() {
        slettAltIDatabase()
        wiremockServer.resetAll()
    }

    @AfterAll
    fun ryddOpp() {
        mockOAuth2Server.shutdown()
        javalin.stop()
    }

    @Test
    fun `Skal oppdatere arbeidsgivers vurdering og returnerer 200 OK`() {
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

        val body = """
            {
              "arbeidsgiversVurdering": "FÅTT_JOBBEN"
            }
        """.trimIndent()

        val (_, response) = fuel
            .put("http://localhost:9000/kandidat/${kandidat.uuid}/vurdering")
            .jsonBody(body)
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        assertThat(response.statusCode).isEqualTo(200)
        val kandidatFraDatabasen = repository.hentKandidat(kandidat.aktørId, kandidatliste.id!!)
        assertThat(kandidatFraDatabasen!!.arbeidsgiversVurdering).isEqualTo(Kandidat.ArbeidsgiversVurdering.FÅTT_JOBBEN)
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

        val (_, response) = fuel
            .put("http://localhost:9000/kandidat/${kandidat.uuid}/vurdering")
            .jsonBody(body)
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        assertThat(response.statusCode).isEqualTo(400)
        val kandidatFraDatabasen = repository.hentKandidat(kandidat.aktørId, kandidatliste.id!!)
        assertThat(kandidatFraDatabasen!!.arbeidsgiversVurdering).isEqualTo(kandidat.arbeidsgiversVurdering)
        assertThat(kandidatFraDatabasen.sistEndret).isEqualToIgnoringNanos(kandidat.sistEndret)
    }

    @Test
    @Disabled("Fungerer lokalt men feiler på GHA")
    fun `Kall med ukjent verdi i vurderingsfeltet skal returnere 400`() {
        val organisasjoner = listOf(Testdata.lagAltinnOrganisasjon("Et Navn", "53987549"))
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)
        val body = """
            {
              "arbeidsgiversVurdering": "NY"
            }
        """.trimIndent()

        val (_, response) = fuel
            .put("http://localhost:9000/kandidat/${UUID.randomUUID()}/vurdering")
            .jsonBody(body)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(400)
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

        val (_, response) = fuel
            .put("http://localhost:9000/kandidat/${UUID.randomUUID()}/vurdering")
            .jsonBody(body)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(400)
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

        val (_, response) = fuel
            .put("http://localhost:9000/kandidat/${kandidat.uuid}/vurdering")
            .jsonBody(body)
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        assertThat(response.statusCode).isEqualTo(403)
    }
}
