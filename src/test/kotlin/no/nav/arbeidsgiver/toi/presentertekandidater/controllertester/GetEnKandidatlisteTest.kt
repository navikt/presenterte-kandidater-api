package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import com.fasterxml.jackson.databind.JsonNode
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import io.javalin.Javalin
import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.OpenSearchKlient
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.*
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetEnKandidatlisteTest {
    private val mockOAuth2Server = MockOAuth2Server()
    private val wiremockServer = WireMockServer(0)
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    private val fuel = FuelManager()
    private lateinit var openSearchKlient: OpenSearchKlient
    private lateinit var javalin: Javalin

    @BeforeAll
    fun init() {
        wiremockServer.start()
        val envs = envs(wiremockServer.port())
        openSearchKlient = OpenSearchKlient(
            mapOf(
                "OPEN_SEARCH_URI" to "http://localhost:${wiremockServer.port()}",
                "OPEN_SEARCH_USERNAME" to "gunnar",
                "OPEN_SEARCH_PASSWORD" to "xyz"
            )
        )
        javalin = opprettJavalinMedTilgangskontrollForTest(issuerProperties, envs)
        mockOAuth2Server.start(port = 18301)

        startLocalApplication(
            javalin = javalin,
            envs = envs,
            openSearchKlient = openSearchKlient
        )
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

    @Test
    fun `Skal returnere en kandidatliste og kandidater med CV`() {
        val stillingId = UUID.randomUUID()
        val endepunkt = "http://localhost:9000/kandidatliste/$stillingId"
        val nå = ZonedDateTime.now()
        val virksomhetsnummer = "111111111"
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummer),
        )
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)


        repository.lagre(kandidatliste().copy(stillingId = stillingId, virksomhetsnummer = virksomhetsnummer))

        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat1 = Kandidat(
            aktørId = "1234",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = nå
        )
        val kandidat2 = Kandidat(
            aktørId = "666",
            kandidatlisteId = kandidatliste.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = nå
        )

        repository.lagre(kandidat1)
        repository.lagre(kandidat2)

        val esRespons = Testdata.flereKandidaterFraES(aktørId1 = kandidat1.aktørId, aktørid2 = kandidat2.aktørId)
        stubHentingAvKandidater(
            requestBody = openSearchKlient.lagBodyForHentingAvCver(
                listOf(
                    kandidat1.aktørId,
                    kandidat2.aktørId
                )
            ), responsBody = esRespons
        )

        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(200)

        val jsonbody = response.body().asString("application/json;charset=utf-8")
        val kandidatlisteMedKandidaterJson = defaultObjectMapper.readTree(jsonbody)
        val kandidatlisteJson = kandidatlisteMedKandidaterJson["kandidatliste"]
        val kandidaterJson = kandidatlisteMedKandidaterJson["kandidater"]

        assertNull(kandidatlisteJson["id"])
        Assertions.assertThat(kandidatlisteJson["status"].textValue()).isEqualTo(Kandidatliste.Status.ÅPEN.toString())
        Assertions.assertThat(kandidatlisteJson["tittel"].textValue()).isEqualTo(kandidatliste.tittel)
        Assertions.assertThat(ZonedDateTime.parse(kandidatlisteJson["sistEndret"].textValue()))
            .isEqualTo(kandidatliste.sistEndret)
        Assertions.assertThat(ZonedDateTime.parse(kandidatlisteJson["opprettet"].textValue()))
            .isEqualTo(kandidatliste.opprettet)
        Assertions.assertThat(kandidatlisteJson["slettet"].asBoolean()).isFalse
        Assertions.assertThat(UUID.fromString(kandidatlisteJson["stillingId"].textValue())).isEqualTo(stillingId)
        Assertions.assertThat(UUID.fromString(kandidatlisteJson["uuid"].textValue())).isEqualTo(kandidatliste.uuid)
        Assertions.assertThat(kandidatlisteJson["virksomhetsnummer"].textValue())
            .isEqualTo(kandidatliste.virksomhetsnummer)

        Assertions.assertThat(kandidaterJson).hasSize(2)
        assertKandidat(kandidaterJson[0], kandidat1)
        assertKandidat(kandidaterJson[1], kandidat2)
        assertNotNull(kandidaterJson[0]["cv"]);
        assertNotNull(kandidaterJson[1]["cv"]);
    }

    @Test
    fun `Skal returnere 403 når man ikke representerer virksomheten kandidatlista tilhører`() {
        val virksomhetsnummerManRepresenterer = "987654321"
        val virksomhetsnummerTilkandidatlista = "123456789"
        val stillingId = UUID.fromString("4bd2c240-92d2-4166-ac54-ba3d21bfbc07")
        val endepunkt = "http://localhost:9000/kandidatliste/$stillingId"
        val nå = ZonedDateTime.now()
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(
            wiremockServer,
            listOf(Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummerManRepresenterer))
        )
        repository.lagre(
            kandidatliste().copy(
                stillingId = stillingId,
                virksomhetsnummer = virksomhetsnummerTilkandidatlista
            )
        )

        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat1 = Kandidat(
            aktørId = "1234",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = nå
        )
        val kandidat2 = Kandidat(
            aktørId = "666",
            kandidatlisteId = kandidatliste.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = nå
        )
        repository.lagre(kandidat1)
        repository.lagre(kandidat2)

        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(403)

        val jsonbody = response.body().asString("application/json;charset=utf-8")
        Assertions.assertThat(jsonbody.isEmpty())
    }

    @Test
    fun `Skal ikke returnere en kandidatliste som er slettet`() {
        val stillingId = UUID.randomUUID()
        val endepunkt = "http://localhost:9000/kandidatliste/$stillingId"
        val virksomhetsnummer = "123456789"
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummer),
        )
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

        repository.lagre(
            kandidatliste().copy(
                stillingId = stillingId,
                slettet = true,
                virksomhetsnummer = virksomhetsnummer
            )
        )

        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(404)
    }

    private fun assertKandidat(fraRespons: JsonNode, fraDatabasen: Kandidat) {
        Assertions.assertThat(fraRespons["kandidat"]).isNotEmpty
        assertNull(fraRespons["kandidat"]["id"])
        Assertions.assertThat(UUID.fromString(fraRespons["kandidat"]["uuid"].textValue())).isEqualTo(fraDatabasen.uuid)
        Assertions.assertThat(
            fraRespons["kandidat"]["arbeidsgiversVurdering"].textValue()
                .equals(fraDatabasen.arbeidsgiversVurdering.name)
        )
        Assertions.assertThat(ZonedDateTime.parse(fraRespons["kandidat"]["sistEndret"].textValue()) == fraDatabasen.sistEndret)
    }

    private fun stubHentingAvKandidater(requestBody: String, responsBody: String) {
        wiremockServer.stubFor(
            WireMock.post("/veilederkandidat_current/_search")
                .withBasicAuth("gunnar", "xyz")
                .withRequestBody(WireMock.containing(requestBody))
                .willReturn(WireMock.ok(responsBody))
        )
    }
}
