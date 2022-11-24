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
class KonverteringTest {
    private val mockOAuth2Server = MockOAuth2Server()
    private val javalin = opprettJavalinMedTilgangskontroll(issuerProperties)
    private val repository = opprettTestRepositoryMedLokalPostgres()
    private val wiremockServer = WireMockServer(8889)
    private val fuel = FuelManager()

    lateinit var openSearchKlient: OpenSearchKlient

    @BeforeAll
    fun init() {
        mockOAuth2Server.start(port = 18302)
        wiremockServer.start()
        openSearchKlient = OpenSearchKlient(
            mapOf(
                "OPEN_SEARCH_URI" to "http://localhost:${wiremockServer.port()}",
                "OPEN_SEARCH_USERNAME" to "gunnar",
                "OPEN_SEARCH_PASSWORD" to "xyz"
            )
        )

        startLocalApplication(javalin = javalin, repository = repository, openSearchKlient = openSearchKlient)

        stubHentingAvAktørId(kandidatnr = "PAM0133wq2mdl", aktørId = "10001000101")
        stubHentingAvAktørId(kandidatnr ="PAM013tc53ryp", aktørId = "10001000102")
        stubHentingAvAktørId(kandidatnr ="PAM01897xkdyc", aktørId = "10001000103")
        stubHentingAvAktørId(kandidatnr ="PAM0v81m8kg0", aktørId = "10001000104")

        val (_, response) = fuel
            .post("http://localhost:9000/internal/konverterdata")
            .response()

        assertThat(response.statusCode).isEqualTo(200)
    }

    @AfterAll
    fun cleanUp() {
        mockOAuth2Server.shutdown()
        javalin.stop()
        wiremockServer.shutdown()
    }

    @Test
    fun `Konvertering av data lagres riktig i databasen med aktørid om person finnes i OpenSearch`() {

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
    fun `Konvertering av data lagres riktig i databasen med aktørid om person IKKE finnes i OpenSearch`() {

        val liste = repository.hentKandidatliste(UUID.fromString("3f381730-bf29-4345-b636-9961fcb42951"))!!
        assertThat(liste.virksomhetsnummer).isEqualTo("926698826")
        assertThat(liste.stillingId).isEqualTo(UUID.fromString("3f381730-bf29-4345-b636-9961fcb42951"))

        val kandiater = repository.hentKandidater(liste.id!!)
        assertThat(kandiater[0].kandidatlisteId).isEqualTo(liste.id!!)
        assertThat(kandiater[0].aktørId).isEqualTo("")

    }

    private fun stubHentingAvAktørId(kandidatnr: String, aktørId: String) {

        wiremockServer.stubFor(
            WireMock.post("/veilederkandidat_current/_search")
                .withBasicAuth("gunnar", "xyz")
                .withRequestBody(WireMock.containing(kandidatnr))
                .willReturn(WireMock.ok(Testdata.aktørIdFraOpenSearch(aktørId)))
        )
    }

}
