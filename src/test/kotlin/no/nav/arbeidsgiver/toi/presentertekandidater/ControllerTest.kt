package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
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
import org.assertj.core.api.Assertions.within
import java.time.temporal.ChronoUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ControllerTest {
    private val mockOAuth2Server = MockOAuth2Server()
    private val javalin = opprettJavalinMedTilgangskontroll(issuerProperties)
    private val repository = opprettTestRepositoryMedLokalPostgres()
    private val wiremockServer = WireMockServer(8888)
    private val fuel = FuelManager()

    lateinit var openSearchKlient: OpenSearchKlient

    @BeforeAll
    fun init() {
        mockOAuth2Server.start(port = 18301)
        wiremockServer.start()
        openSearchKlient = OpenSearchKlient(
            mapOf(
                "OPEN_SEARCH_URI" to "http://localhost:${wiremockServer.port()}",
                "OPEN_SEARCH_USERNAME" to "gunnar",
                "OPEN_SEARCH_PASSWORD" to "xyz"
            )
        )

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
        val (_, response) = fuel
            .get(endepunkt)
            .response()

        assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `GET mot kandidatlister-endepunkt svarer 403 Forbidden hvis forespørselens token er ugyldig`() {
        val endepunkt = "http://localhost:9000/kandidatlister"
        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentUgyldigToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `GET mot kandidatlister-endepunkt uten virksomhetsnummer svarer 400 Bad Request`() {
        val endepunkt = "http://localhost:9000/kandidatlister"
        val (_, response) = fuel
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
        val kandidatliste = kandidatliste().copy(
            virksomhetsnummer = virksomhetsnummer,
            stillingId = stillingId
        )
        repository.lagre(kandidatliste)

        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(200)

        val kandidatlisteMedKandidaterJson =
            defaultObjectMapper.readTree(response.body().asString("application/json;charset=utf-8"))
        val kandidatlisteJson = kandidatlisteMedKandidaterJson[0]["kandidatliste"]
        val antallKandidater = kandidatlisteMedKandidaterJson[0]["antallKandidater"]
        assertThat(antallKandidater.asInt()).isZero()
        assertThat(UUID.fromString(kandidatlisteJson["uuid"].textValue())).isEqualTo(kandidatliste.uuid)
        assertThat(UUID.fromString(kandidatlisteJson["stillingId"].textValue())).isEqualTo(stillingId)
        assertThat(kandidatlisteJson["virksomhetsnummer"].textValue()).isEqualTo(virksomhetsnummer)
        assertThat(kandidatlisteJson["slettet"].asBoolean()).isFalse
        assertThat(kandidatlisteJson["status"].textValue()).isEqualTo(Kandidatliste.Status.ÅPEN.toString())
        assertThat(kandidatlisteJson["tittel"].textValue()).isEqualTo("Tittel")
        assertNull(kandidatlisteJson["id"])
        assertThat(ZonedDateTime.parse(kandidatlisteJson["sistEndret"].textValue())).isCloseTo(
            kandidatliste.sistEndret,
            within(3, ChronoUnit.SECONDS)
        )
        assertThat(ZonedDateTime.parse(kandidatlisteJson["opprettet"].textValue())).isCloseTo(
            kandidatliste.opprettet,
            within(3, ChronoUnit.SECONDS)
        )

    }

    @Test
    fun `GET mot kandidatliste-endepunkt returnerer en kandidatliste og kandidater med CV`() {
        val stillingId = UUID.fromString("4bd2c240-92d2-4166-ac54-ba3d21bfbc07")
        val endepunkt = "http://localhost:9000/kandidatliste/$stillingId"
        val nå = ZonedDateTime.now()

        repository.lagre(kandidatliste().copy(stillingId = stillingId))

        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat1 = Kandidat(
            aktørId = "1234",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = TIL_VURDERING,
            sistEndret = nå
        )
        val kandidat2 = Kandidat(
            aktørId = "666",
            kandidatlisteId = kandidatliste.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = TIL_VURDERING,
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
        val kandidat = Kandidat(
            aktørId = "1234",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = TIL_VURDERING,
            sistEndret = ZonedDateTime.now().minusDays(1)
        )
        repository.lagre(kandidat)

        val body = """
            {
              "arbeidsgiversVurdering": "FÅTT_JOBBEN"
            }
        """.trimIndent()

        val (_, response) = fuel
            .put("http://localhost:9000/kandidat/${kandidat.uuid}/vurdering")
            .jsonBody(body)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(200)
        val kandidatFraDatabasen = repository.hentKandidat(kandidat.aktørId, kandidatliste.id!!)
        assertThat(kandidatFraDatabasen!!.arbeidsgiversVurdering).isEqualTo(Kandidat.ArbeidsgiversVurdering.FÅTT_JOBBEN)
        assertThat(kandidatFraDatabasen!!.sistEndret).isEqualToIgnoringSeconds(ZonedDateTime.now())
    }

    @Test
    fun `PUT mot vurdering-endepunkt med nullverdi skal returnere 400`() {
        val stillingId = UUID.randomUUID()
        repository.lagre(kandidatliste().copy(stillingId = stillingId))
        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat = Kandidat(
            aktørId = "1234",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = TIL_VURDERING,
            sistEndret = ZonedDateTime.now().minusDays(1)
        )
        repository.lagre(kandidat)

        val body = """
            {
              "arbeidsgiversVurdering": null
            }
        """.trimIndent()

        val (_, response) = fuel
            .put("http://localhost:9000/kandidat/${kandidat.uuid}/vurdering")
            .jsonBody(body)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(400)
        val kandidatFraDatabasen = repository.hentKandidat(kandidat.aktørId, kandidatliste.id!!)
        assertThat(kandidatFraDatabasen!!.arbeidsgiversVurdering).isEqualTo(kandidat.arbeidsgiversVurdering)
        assertThat(kandidatFraDatabasen!!.sistEndret).isEqualToIgnoringNanos(kandidat.sistEndret)
    }

    @Test
    @Disabled("Disablet fordi denne feiler med statuskode -1 av ukjent grunn på GHA, ikke lokalt.")
    fun `PUT mot vurdering-endepunkt med ukjent verdi skal returnere 400`() {
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
    fun `PUT mot vurdering-endepunkt gir 400 hvis kandidat ikke eksisterer`() {
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
    fun `Konvertering av data lagres riktig i databasen`() {
        stubHentingAvAktørId(kandidatnr = "PAM0133wq2mdl", aktørId = "10001000101")
        stubHentingAvAktørId(kandidatnr ="PAM013tc53ryp", aktørId = "10001000102")
        stubHentingAvAktørId(kandidatnr ="PAM01897xkdyc", aktørId = "10001000103")
        stubHentingAvAktørId(kandidatnr ="PAM0v81m8kg0", aktørId = "10001000104")

        val (_, response) = fuel
            .post("http://localhost:9000/internal/konverterdata")
            .response()

        assertThat(response.statusCode).isEqualTo(200)

        val liste = repository.hentKandidatliste(UUID.fromString("24435f0c-bb6b-4a69-b5b9-e53b69a5a994"))!!
        assertThat(liste.virksomhetsnummer).isEqualTo("893119302")
        assertThat(liste.stillingId).isEqualTo(UUID.fromString("24435f0c-bb6b-4a69-b5b9-e53b69a5a994"))

        val kandiater = repository.hentKandidater(liste.id!!)
        assertThat(kandiater[0].kandidatlisteId).isEqualTo(liste.id!!)
        assertThat(kandiater[0].aktørId).isEqualTo("10001000101")
        assertThat(kandiater[1].aktørId).isEqualTo("10001000102")
        assertThat(kandiater[2].aktørId).isEqualTo("10001000103")
        assertThat(kandiater[3].aktørId).isEqualTo("10001000104")

    }

    @Test
    fun `Konvertering av data når kandidtnr ikke finnes i OpenSearch git tom aktørID`() {

        val (_, response) = fuel
            .post("http://localhost:9000/internal/konverterdata")
            .response()

        assertThat(response.statusCode).isEqualTo(200)

        val liste = repository.hentKandidatliste(UUID.fromString("24435f0c-bb6b-4a69-b5b9-e53b69a5a994"))!!
        assertThat(liste.virksomhetsnummer).isEqualTo("893119302")
        assertThat(liste.stillingId).isEqualTo(UUID.fromString("24435f0c-bb6b-4a69-b5b9-e53b69a5a994"))

        val kandiater = repository.hentKandidater(liste.id!!)
        assertThat(kandiater[0].kandidatlisteId).isEqualTo(liste.id!!)
        assertThat(kandiater[0].aktørId).isEqualTo("")

    }

    private fun assertKandidat(fraRespons: JsonNode, fraDatabasen: Kandidat) {
        assertThat(fraRespons["kandidat"]).isNotEmpty
        assertNull(fraRespons["kandidat"]["id"])
        assertThat(UUID.fromString(fraRespons["kandidat"]["uuid"].textValue())).isEqualTo(fraDatabasen.uuid)
        assertThat(
            fraRespons["kandidat"]["arbeidsgiversVurdering"].textValue()
                .equals(fraDatabasen.arbeidsgiversVurdering.name)
        )
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

    private fun stubHentingAvAktørId(kandidatnr: String, aktørId: String) {

        wiremockServer.stubFor(
            WireMock.post("/veilederkandidat_current/_search")
                .withBasicAuth("gunnar", "xyz")
                .withRequestBody(WireMock.containing(kandidatnr))
                .willReturn(WireMock.ok(Testdata.aktørIdFraOpenSearch(aktørId)))
        )
    }

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
