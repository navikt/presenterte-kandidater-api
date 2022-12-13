package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.tomakehurst.wiremock.WireMockServer
import io.javalin.Javalin
import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.*
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeleteKandidatTest {
    private val mockOAuth2Server = MockOAuth2Server()
    private val wiremockServer = WireMockServer(0)
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    private val fuel = FuelManager()
    private lateinit var javalin: Javalin

    @BeforeAll
    fun init() {
        wiremockServer.start()
        val envs = envs(wiremockServer.port())
        javalin = opprettJavalinMedTilgangskontrollForTest(issuerProperties, envs)
        mockOAuth2Server.start(port = 18301)
        startLocalApplication(javalin = javalin, envs = envs)
    }

    @AfterEach
    fun afterEach() {
        wiremockServer.resetAll()
    }

    @AfterAll
    fun cleanUp() {
        mockOAuth2Server.shutdown()
        javalin.stop()
        wiremockServer.shutdown()
    }

    @Disabled
    @Test
    fun `Skal slette kandidat og returnere 200 OK`() {
        val virksomhetsnummer = "987654321"
        val stillingId = UUID.randomUUID()
        val kandidatliste = kandidatliste().copy(
            virksomhetsnummer = virksomhetsnummer,
            stillingId = stillingId
        )
        repository.lagre(kandidatliste)
        val lagretKandidatliste = repository.hentKandidatliste(stillingId)!!
        val kandidat = Kandidat(uuid = UUID.randomUUID(), aktørId = "dummy", kandidatlisteId = lagretKandidatliste.id!!, arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING, sistEndret = ZonedDateTime.now())
        repository.lagre(kandidat)
        val organisasjoner = listOf(Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummer))
        stubHentingAvOrganisasjonerFraAltinnProxy(wiremockServer, organisasjoner)

        val (_, response) = fuel
            .delete("http://localhost:9000/kandidat/${kandidat.uuid}")
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        assertNull(repository.hentKandidat(kandidat.aktørId, kandidat.kandidatlisteId))
    }

    @Disabled
    @Test
    fun `Skal returnere 403 når man ikke representerer virksomheten`() {
        val virksomhetsnummerManIkkeHarRettighetTil = "123456789"
        val virksomhetsnummerManHarRettighetTil = "987654321"
        val stillingId = UUID.randomUUID()
        val kandidatliste = kandidatliste().copy(
            virksomhetsnummer = virksomhetsnummerManIkkeHarRettighetTil,
            stillingId = stillingId
        )
        repository.lagre(kandidatliste)
        val lagretKandidatliste = repository.hentKandidatliste(stillingId)!!
        val kandidat = Kandidat(uuid = UUID.randomUUID(), aktørId = "dummy", kandidatlisteId = lagretKandidatliste.id!!, arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING, sistEndret = ZonedDateTime.now())
        repository.lagre(kandidat)
        val organisasjoner = listOf(Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummerManHarRettighetTil))
        stubHentingAvOrganisasjonerFraAltinnProxy(wiremockServer, organisasjoner)

        val (_, response) = fuel
            .delete("http://localhost:9000/kandidat/${kandidat.uuid}")
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(403)
        val jsonbody = response.body().asString("application/json;charset=utf-8")
        Assertions.assertThat(jsonbody.isEmpty())
    }
}
