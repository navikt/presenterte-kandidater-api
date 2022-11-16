package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.junit.jupiter.api.*
import java.math.BigInteger
import java.time.ZonedDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ControllerTest {

    private val mockOAuth2Server = MockOAuth2Server()
    private val javalin = opprettJavalinMedTilgangskontroll(issuerProperties)
    private val repository = opprettTestRepositoryMedLokalPostgres()
    private val wiremockServer = WireMockServer(8888)
    lateinit var openSearchKlient : OpenSearchKlient

    @BeforeAll
    fun init() {
        mockOAuth2Server.start(port = 18301)
        wiremockServer.start()
        val miljøvariabler = mapOf(
            "OPEN_SEARCH_URI" to "http://localhost:${wiremockServer.port()}",
            "OPEN_SEARCH_USERNAME" to "gunnar",
            "OPEN_SEARCH_PASSWORD" to "xyz"
        )
        openSearchKlient = OpenSearchKlient(miljøvariabler)
        startLocalApplication(javalin = javalin, repository = repository, openSearchKlient = openSearchKlient)

    }

    @AfterAll
    fun cleanUp() {
        mockOAuth2Server.shutdown()
        javalin.stop()
    }

    @Test
    fun `GET mot kandidatlister-endepunkt gir httpstatus 403 uten et gyldig token`() {
        val (_, response) = Fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=123")
            .authentication().bearer(hentUgyldigToken(mockOAuth2Server))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `GET mot kandidater-endepunkt gir httpstatus 403 uten token`() {
        val (_, response) = Fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=123")
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `GET mot kandidaterliste med kandidater gir status 200`() {
        val aktørId1 = "1234"
        val uuidForAktør1 = UUID.fromString("28e2c1f6-dea5-46d1-90cd-bfbd994e06df")
        val aktørId2 = "666"
        val uuidForAktør2 = UUID.fromString("60e2c1f6-dea5-50d1-93cd-bfbd224e08df")

        val esRepons = Testdata.flereKandidaterFraES(aktørId1 = aktørId1, aktørid2 = aktørId2)
        stubHentingAvKandidater(requestBody = openSearchKlient.lagBodyForHentingAvCver(listOf(aktørId1, aktørId2)), responsBody = esRepons)

        val stillingId = UUID.fromString("4bd2c240-92d2-4166-ac54-ba3d21bfbc07")
        repository.lagre(kandidatliste().copy(stillingId = stillingId))
        val kandidatliste = repository.hentKandidatliste(stillingId)
        repository.lagre(
            Kandidat(
                aktørId = aktørId1,
                kandidatlisteId = kandidatliste?.id!!,
                uuid = uuidForAktør1
            )
        )
        repository.lagre(
            Kandidat(aktørId = aktørId2,
                kandidatlisteId = kandidatliste?.id!!,
                uuid = uuidForAktør2
            )
        )
        val (_, response) = Fuel
            .get("http://localhost:9000/kandidatliste/$stillingId")
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        val jsonbody = response.body().asString("application/json;charset=utf-8")
        val kandidatlisteJson = defaultObjectMapper.readTree(jsonbody)
        Assertions.assertThat(kandidatlisteJson["kandidatliste"]).isNotEmpty
        Assertions.assertThat(kandidatlisteJson["kandidater"]).hasSize(2)
        Assertions.assertThat(kandidatlisteJson["kandidater"][0]["kandidat"]).isNotEmpty
        Assertions.assertThat(kandidatlisteJson["kandidater"][1]["kandidat"]).isNotEmpty
        Assertions.assertThat(kandidatlisteJson["kandidater"][0]["kandidat"]["uuid"].asText()).isEqualTo(uuidForAktør1.toString())
        Assertions.assertThat(kandidatlisteJson["kandidater"][1]["kandidat"]["uuid"].asText()).isEqualTo(uuidForAktør2.toString())
        Assertions.assertThat(kandidatlisteJson["kandidater"][0]["cv"]).isNotEmpty
        Assertions.assertThat(kandidatlisteJson["kandidater"][0]["cv"]["aktørId"]).isNull()
        Assertions.assertThat(kandidatlisteJson["kandidater"][1]["cv"]["aktørId"]).isNull()
        Assertions.assertThat(kandidatlisteJson["kandidater"][0]["cv"]["sammendrag"].asText()).contains("Er fanatisk opptatt av religion")
    }

    @Test
    fun `GET mot kandidaterliste gir status 200`() {
        val uuid = UUID.randomUUID()

        repository.lagre(
            kandidatliste().copy(
                virksomhetsnummer = "123456788",
                stillingId = uuid
            )
        )
        val (_, response) = Fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=123456788")
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(response.body().asString("application/json;charset=utf-8"))
            .contains(("""
                [
                  {
                    "kandidatliste": {
                      "uuid":"7ea380f8-a0af-433f-8cbc-51c5788a7d29",
                      "stillingId": "$uuid",
                      "tittel": "Tittel",
                      "status": "ÅPEN",
                      "slettet": false,
                      "virksomhetsnummer": "123456788",
                      "sistEndret":"
                     """.filter { !it.isWhitespace() }))

            .contains("""  
                      "
                    },
                    "antallKandidater": 0
                  }
                ]
            """.filter { !it.isWhitespace() })
    }

    private fun kandidatliste(uuid: UUID = UUID.randomUUID()) = Kandidatliste(
        stillingId = uuid,
        tittel = "Tittel",
        status = Kandidatliste.Status.ÅPEN,
        virksomhetsnummer = "123456789",
        uuid = UUID.fromString("7ea380f8-a0af-433f-8cbc-51c5788a7d29"),
        sistEndret = ZonedDateTime.parse("2022-11-15T14:46:39.051+01:00"),
        opprettet = ZonedDateTime.parse("2022-11-15T14:46:37.50899+01:00")
    )

    private fun kandidat(uuid: UUID = UUID.randomUUID(), kandidatlisteid: BigInteger) = Kandidat(
        uuid = uuid,
        aktørId = "123",
        kandidatlisteId = kandidatlisteid
    )

    fun stubHentingAvKandidater(requestBody: String, responsBody: String) {
        wiremockServer.stubFor(
            WireMock.post("/veilederkandidat_current/_search")
            .withBasicAuth("gunnar", "xyz")
            .withRequestBody(WireMock.containing(requestBody))
            .willReturn(WireMock.ok(responsBody)))
    }
}
