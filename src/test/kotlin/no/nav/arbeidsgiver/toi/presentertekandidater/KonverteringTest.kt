package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.kittinunf.fuel.core.FuelManager
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.arbeidsgiver.toi.presentertekandidater.Kandidat.ArbeidsgiversVurdering
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.TokendingsKlient
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.io.File
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KonverteringTest {
    private val mockOAuth2Server = MockOAuth2Server()
    private val altinnKlient = AltinnKlient(envs, TokendingsKlient(envs))
    private val javalin = opprettJavalinMedTilgangskontrollForTest(issuerProperties)
    private val repository = opprettTestRepositoryMedLokalPostgres()
    private val wiremockServer = WireMockServer(8889)
    private val fuel = FuelManager()
    lateinit var openSearchKlient: OpenSearchKlient
    lateinit var konverteringFilstier: KonverteringFilstier

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
        konverteringFilstier = KonverteringFilstier(
            mapOf(Pair("NAIS_CLUSTER_NAME", "test"))
        )

        startLocalApplication(
            javalin = javalin,
            repository = repository,
            openSearchKlient = openSearchKlient,
            konverteringsfilstier = konverteringFilstier
        )

        stubHentingAvAktørId(kandidatnr = "PAM0133wq2mdl", aktørId = "10001000101") // ag-status: NY
        stubHentingAvAktørId(kandidatnr = "PAM013tc53ryp", aktørId = "10001000102") // ag-status: PAABEGYNT
        stubHentingAvAktørId(kandidatnr = "PAM01897xkdyc", aktørId = "10001000103") // ag-status: AKTUELL
        stubHentingAvAktørId(kandidatnr = "PAM0v81m8kg0", aktørId = "10001000104") // ag-status: IKKE_AKTUELL

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
    fun `Konvertering av kandidatliste lagres riktig`() {

        val liste = repository.hentKandidatliste(UUID.fromString("24435f0c-bb6b-4a69-b5b9-e53b69a5a994"))!!
        assertThat(liste.stillingId).isEqualTo(UUID.fromString("24435f0c-bb6b-4a69-b5b9-e53b69a5a994"))
        assertThat(liste.tittel).isEqualTo("Brenner du for gaming?")
        assertThat(liste.virksomhetsnummer).isEqualTo("893119302")
        assertThat(liste.opprettet.toString()).isEqualTo("2022-06-27T12:50:38+02:00[Europe/Oslo]")
        assertThat(liste.status).isEqualTo(Kandidatliste.Status.ÅPEN)
        assertThat(liste.slettet).isFalse
    }

    @Test
    fun `Konvertering av data lagres med status riktig i databasen på aktørid om person finnes i OpenSearch`() {

        val liste = repository.hentKandidatliste(UUID.fromString("24435f0c-bb6b-4a69-b5b9-e53b69a5a994"))!!

        val kandiater = repository.hentKandidater(liste.id!!)
        assertThat(kandiater[0].kandidatlisteId).isEqualTo(liste.id!!)
        assertThat(kandiater[0].aktørId).isEqualTo("10001000101")
        assertThat(kandiater[0].arbeidsgiversVurdering).isEqualTo(ArbeidsgiversVurdering.TIL_VURDERING) // NY -> TIL_VURDERING
        assertThat(kandiater[1].aktørId).isEqualTo("10001000102")
        assertThat(kandiater[1].arbeidsgiversVurdering).isEqualTo(ArbeidsgiversVurdering.TIL_VURDERING) // PAABEGYNT -> TIL_VURDERING
        assertThat(kandiater[2].aktørId).isEqualTo("10001000103")
        assertThat(kandiater[2].arbeidsgiversVurdering).isEqualTo(ArbeidsgiversVurdering.AKTUELL) // AKTUELL -> AKTUELL
        assertThat(kandiater[3].aktørId).isEqualTo("10001000104")
        assertThat(kandiater[3].arbeidsgiversVurdering).isEqualTo(ArbeidsgiversVurdering.IKKE_AKTUELL) // IKKE_AKTUELL -> IKKE_AKTUELL
    }

    @Test
    fun `Konvertering av kandidat skal gi riktig sist endret tidspunkt`() {
        val liste = repository.hentKandidatliste(UUID.fromString("24435f0c-bb6b-4a69-b5b9-e53b69a5a994"))!!
        val kandiater = repository.hentKandidater(liste.id!!)
        assertThat(kandiater[0].aktørId).isEqualTo("10001000101")

        assertThat(kandiater[0].sistEndret.toString()).isEqualTo("2022-08-16T02:35:43+02:00[Europe/Oslo]")
    }


    @Test
    fun `Konvertering av data lagres riktig i databasen med aktørid om person IKKE finnes i OpenSearch`() {

        val liste = repository.hentKandidatliste(UUID.fromString("3f381730-bf29-4345-b636-9961fcb42951"))!!
        assertThat(liste.virksomhetsnummer).isEqualTo("926698826")
        assertThat(liste.stillingId).isEqualTo(UUID.fromString("3f381730-bf29-4345-b636-9961fcb42951"))

        val kandiater = repository.hentKandidater(liste.id!!)
        assertThat(kandiater[0].kandidatlisteId).isEqualTo(liste.id!!)
        assertThat(kandiater[0].aktørId).isEqualTo("")
        assertThat(kandiater[0].arbeidsgiversVurdering).isEqualTo(ArbeidsgiversVurdering.TIL_VURDERING)


    }

    @Test
    fun `Filsti for test dev og prod skal være riktig`() {
        val testEnv = mapOf(Pair("NAIS_CLUSTER_NAME", "test"))
        val devEnv = mapOf(Pair("NAIS_CLUSTER_NAME", "dev-gcp"))
        val prodEnv = mapOf(Pair("NAIS_CLUSTER_NAME", "prod-gcp"))

        val testFilstier = KonverteringFilstier(testEnv)
        val devFilstier = KonverteringFilstier(devEnv)
        val prodFilstier = KonverteringFilstier(prodEnv)

        assertThat(testFilstier.kandidatlistefil).isEqualTo(File("./src/test/resources/kandidatlister-konvertering.json"))
        assertThat(testFilstier.kandidatfil).isEqualTo(File("./src/test/resources/kandidater-konvertering.json"))
        assertThat(devFilstier.kandidatlistefil).isEqualTo(File("./tmp/kandidatlister-konvertering.json"))
        assertThat(devFilstier.kandidatfil).isEqualTo(File("./tmp/kandidater-konvertering.json"))
        assertThat(prodFilstier.kandidatlistefil).isEqualTo(File("./tmp/kandidatlister-konvertering.json"))
        assertThat(prodFilstier.kandidatfil).isEqualTo(File("./tmp/kandidater-konvertering.json"))
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
