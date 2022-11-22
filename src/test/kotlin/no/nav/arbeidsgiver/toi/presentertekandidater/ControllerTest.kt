package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.databind.JsonNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import no.nav.arbeidsgiver.toi.presentertekandidater.Kandidat.ArbeidsgiversVurdering.TIL_VURDERING
import no.nav.helse.rapids_rivers.asLocalDateTime

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
        openSearchKlient = OpenSearchKlient(mapOf(
            "OPEN_SEARCH_URI" to "http://localhost:${wiremockServer.port()}",
            "OPEN_SEARCH_USERNAME" to "gunnar",
            "OPEN_SEARCH_PASSWORD" to "xyz"
        ))

        startLocalApplication(javalin = javalin, repository = repository, openSearchKlient = openSearchKlient)
    }

    @AfterAll
    fun cleanUp() {
        mockOAuth2Server.shutdown()
        javalin.stop()
    }

    @Test
    fun `GET mot kandidatlister-endepunkt svarer 403 Forbidden hvis forespørselen ikke inneholder et token`() {
        val endepunkt = "http://localhost:9000/kandidatlister"
        val (_, response) = Fuel
            .get(endepunkt)
            .response()

        assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `GET mot kandidatlister-endepunkt svarer 403 Forbidden hvis forespørselens token er ugyldig`() {
        val endepunkt = "http://localhost:9000/kandidatlister"
        val (_, response) = Fuel
            .get(endepunkt)
            .authentication().bearer(hentUgyldigToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `GET mot kandidatlister-endepunkt uten virksomhetsnummer svarer 400 Bad Request`() {
        val endepunkt = "http://localhost:9000/kandidatlister"
        val (_, response) = Fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(400)
    }

    @Test
    fun `GET mot kandidatlister-endepunkt returnerer 200 OK med alle kandidatlister tilknyttet oppgitt virksomhetsnummer`() {
        val stillingId = UUID.randomUUID()
        val endepunkt = "http://localhost:9000/kandidatlister?virksomhetsnummer=123456788"
        val virksomhetsnummer = "123456788"

        repository.lagre(
            kandidatliste().copy(
                virksomhetsnummer = virksomhetsnummer,
                stillingId = stillingId
            )
        )

        val (_, response) = Fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        val fraDatabase = repository.hentKandidatliste(stillingId)
        assertThat(response.statusCode).isEqualTo(200)
        assertThat(response.body().asString("application/json;charset=utf-8"))
            .contains("""
                [
                    {
                        "kandidatliste": {
                            "uuid":"7ea380f8-a0af-433f-8cbc-51c5788a7d29",
                            "stillingId": "$stillingId",
                            "tittel": "Tittel",
                            "status": "ÅPEN",
                            "slettet": false,
                            "virksomhetsnummer": "$virksomhetsnummer",
                            "sistEndret": "${ZonedDateTime.from(fraDatabase?.sistEndret).toOffsetDateTime()}",
                            "opprettet": "${ZonedDateTime.from(fraDatabase?.opprettet).toOffsetDateTime()}"
                        },
                        "antallKandidater": 0
                    }
                ]
            """.removeWhitespace())
    }

    @Test
    fun `GET mot kandidatliste-endepunkt returnerer en kandidatliste og kandidater med CV`() {
        val stillingId = UUID.fromString("4bd2c240-92d2-4166-ac54-ba3d21bfbc07")
        val endepunkt = "http://localhost:9000/kandidatliste/$stillingId"
        val nå = ZonedDateTime.now()

        repository.lagre(kandidatliste().copy(stillingId = stillingId))

        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat1 = Kandidat(aktørId = "1234", kandidatlisteId = kandidatliste?.id!!, uuid = UUID.randomUUID(), arbeidsgiversVurdering = TIL_VURDERING, sistEndret = nå)
        val kandidat2 = Kandidat(aktørId = "666", kandidatlisteId = kandidatliste.id!!, uuid = UUID.randomUUID(), arbeidsgiversVurdering = TIL_VURDERING, sistEndret = nå)

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

        val (_, response) = Fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(200)

        val jsonbody = response.body().asString("application/json;charset=utf-8")
        val kandidatlisteMedKandidaterJson = defaultObjectMapper.readTree(jsonbody)
        val kandidatlisteJson = kandidatlisteMedKandidaterJson["kandidatliste"]
        val kandidaterJson = kandidatlisteMedKandidaterJson["kandidater"]

        assertNull(kandidatlisteJson["id"])
        assertThat(kandidatlisteJson["status"].textValue()).isEqualTo(Kandidatliste.Status.ÅPEN.toString())
        assertThat(kandidatlisteJson["tittel"].textValue()).isEqualTo(kandidatliste.tittel)
        assertThat(ZonedDateTime.parse(kandidatlisteJson["sistEndret"].textValue())).isEqualTo(kandidatliste.sistEndret)
        assertThat(ZonedDateTime.parse(kandidatlisteJson["opprettet"].textValue())).isEqualTo(kandidatliste.opprettet)
        assertThat(kandidatlisteJson["slettet"].asBoolean()).isFalse
        assertThat(UUID.fromString(kandidatlisteJson["stillingId"].textValue())).isEqualTo(stillingId)
        assertThat(UUID.fromString(kandidatlisteJson["uuid"].textValue())).isEqualTo(kandidatliste.uuid)
        assertThat(kandidatlisteJson["virksomhetsnummer"].textValue()).isEqualTo(kandidatliste.virksomhetsnummer)

        assertThat(kandidaterJson).hasSize(2)
        assertKandidat(kandidaterJson[0], kandidat1)
        assertKandidat(kandidaterJson[1], kandidat2)
        assertNotNull(kandidaterJson[0]["cv"]);
        assertNotNull(kandidaterJson[1]["cv"]);
    }

    @Test
    fun `PUT mot vurdering-endepunkt oppdaterer arbeidsgivers vurdering og returnerer 200 OK`() {
        val stillingId = UUID.randomUUID()
        repository.lagre(kandidatliste().copy(stillingId = stillingId))
        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat = Kandidat(aktørId = "1234", kandidatlisteId = kandidatliste?.id!!, uuid = UUID.randomUUID(), arbeidsgiversVurdering = TIL_VURDERING, sistEndret = ZonedDateTime.now().minusDays(1))
        repository.lagre(kandidat)

        val body = """
            {
              "vurdering": "FÅTT_JOBBEN"
            }
        """.trimIndent()

        val (_, response) = Fuel
            .put("http://localhost:9000/kandidat/${kandidat.uuid}/vurdering")
            .body(body)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(200)
        val kandidatFraDatabasen = repository.hentKandidat(kandidat.aktørId, kandidatliste.id!!)
        assertThat(kandidatFraDatabasen!!.arbeidsgiversVurdering).isEqualTo(Kandidat.ArbeidsgiversVurdering.FÅTT_JOBBEN)
        assertThat(kandidatFraDatabasen!!.sistEndret).isEqualToIgnoringSeconds(ZonedDateTime.now())
    }

    private fun assertKandidat(fraRespons: JsonNode, fraDatabasen: Kandidat) {
        assertThat(fraRespons["kandidat"]).isNotEmpty
        assertNull(fraRespons["kandidat"]["id"])
        assertThat(UUID.fromString(fraRespons["kandidat"]["uuid"].textValue())).isEqualTo(fraDatabasen.uuid)
        assertThat(fraRespons["kandidat"]["arbeidsgiversVurdering"].textValue().equals(fraDatabasen.arbeidsgiversVurdering.name))
        assertThat(ZonedDateTime.parse(fraRespons["kandidat"]["sistEndret"].textValue()) == fraDatabasen.sistEndret)
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

    private fun stubHentingAvKandidater(requestBody: String, responsBody: String) {
        wiremockServer.stubFor(
            WireMock.post("/veilederkandidat_current/_search")
                .withBasicAuth("gunnar", "xyz")
                .withRequestBody(WireMock.containing(requestBody))
                .willReturn(WireMock.ok(responsBody))
        )
    }

    private fun String.removeWhitespace() = this.filter { !it.isWhitespace() }
}
