package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.databind.JsonNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.math.BigInteger
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ControllerTest {

    private val mockOAuth2Server = MockOAuth2Server()
    private val javalin = opprettJavalinMedTilgangskontroll(issuerProperties)
    private val repository = opprettTestRepositoryMedLokalPostgres()
    private val wiremockServer = WireMockServer(8888)
    lateinit var openSearchKlient: OpenSearchKlient

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
    fun `GET mot kandidatliste med kandidater gir status 200`() {
        val stillingId = UUID.fromString("4bd2c240-92d2-4166-ac54-ba3d21bfbc07")
        repository.lagre(kandidatliste().copy(stillingId = stillingId))
        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat1 = Kandidat(aktørId = "1234", kandidatlisteId = kandidatliste?.id!!, uuid = UUID.randomUUID())
        val kandidat2 = Kandidat(aktørId = "666", kandidatlisteId = kandidatliste?.id!!, uuid = UUID.randomUUID())
        repository.lagre(kandidat1)
        repository.lagre(kandidat2)
        val esRespons = Testdata.flereKandidaterFraES(aktørId1 = kandidat1.aktørId, aktørid2 = kandidat2.aktørId)
        stubHentingAvKandidater(requestBody = openSearchKlient.lagBodyForHentingAvCver(
                listOf(
                    kandidat1.aktørId,
                    kandidat2.aktørId
                )
            ), responsBody = esRespons
        )

        val (_, response) = Fuel
            .get("http://localhost:9000/kandidatliste/$stillingId")
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(200)
        val jsonbody = response.body().asString("application/json;charset=utf-8")
        val kandidatlisteMedKandidaterJson = defaultObjectMapper.readTree(jsonbody)
        val kandidatlisteJson = kandidatlisteMedKandidaterJson["kandidatliste"]

        assertNull(kandidatlisteJson["id"])
        assertThat(kandidatlisteJson["status"].textValue()).isEqualTo(Kandidatliste.Status.ÅPEN.toString())
        assertThat(kandidatlisteJson["tittel"].textValue()).isEqualTo(kandidatliste.tittel)
        assertThat(ZonedDateTime.parse(kandidatlisteJson["sistEndret"].textValue())).isEqualTo(kandidatliste.sistEndret)
        assertThat(ZonedDateTime.parse(kandidatlisteJson["opprettet"].textValue())).isEqualTo(kandidatliste.opprettet)
        assertThat(kandidatlisteJson["slettet"].asBoolean()).isFalse
        assertThat(UUID.fromString(kandidatlisteJson["stillingId"].textValue())).isEqualTo(stillingId)
        assertThat(UUID.fromString(kandidatlisteJson["uuid"].textValue())).isEqualTo(kandidatliste.uuid)
        assertThat(kandidatlisteJson["virksomhetsnummer"].textValue()).isEqualTo(kandidatliste.virksomhetsnummer)

//        val førsteKandidatFraRespons = kandidatlisteMedKandidaterFraRespons.kandidater.first()
//        val andreKandidatFraRespons = kandidatlisteMedKandidaterFraRespons.kandidater[1]
//        assertKandidat(førsteKandidatFraRespons.kandidat, kandidat1)
//        assertKandidat(andreKandidatFraRespons.kandidat1, kandidat2)

        val kandidaterJson = kandidatlisteMedKandidaterJson["kandidater"]
        assertThat(kandidaterJson).hasSize(2)
        assertThat(kandidaterJson[0]["kandidat"]).isNotEmpty
        assertThat(kandidaterJson[1]["kandidat"]).isNotEmpty
        assertThat(UUID.fromString(kandidaterJson[0]["kandidat"]["uuid"].textValue())).isEqualTo(kandidat1.uuid)
        assertThat(UUID.fromString(kandidaterJson[1]["kandidat"]["uuid"].textValue())).isEqualTo(kandidat2.uuid)
        assertThat(kandidaterJson[0]["cv"]).isNotEmpty
        assertThat(kandidaterJson[0]["cv"]["aktørId"]).isNull()
        assertThat(kandidaterJson[1]["cv"]["aktørId"]).isNull()
        assertThat(kandidaterJson[0]["cv"]["sammendrag"].asText()).contains("Er fanatisk opptatt av religion")
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
                     """.filter { !it.isWhitespace() })
            )

            .contains("""  
                      "
                    },
                    "antallKandidater": 0
                  }
                ]
            """.filter { !it.isWhitespace() })
    }

    private fun assertKandidat(fraRespons: JsonNode, fraDatabasen: Kandidat) {
    }

    private fun assertCv(faktiskCv: Cv, reellCv: Cv) {
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
                .willReturn(WireMock.ok(responsBody))
        )
    }
}
