package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.flereKandidaterFraES
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Cv
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.OpenSearchKlient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenSearchKlientTest {
    private val wiremockServer = WireMockServer(9999)
    private lateinit var openSearchKlient: OpenSearchKlient

    @BeforeAll
    fun setUp() {
        wiremockServer.start()
        val miljøvariabler = mapOf(
            "OPEN_SEARCH_URI" to "http://localhost:${wiremockServer.port()}",
            "OPEN_SEARCH_USERNAME" to "gunnar",
            "OPEN_SEARCH_PASSWORD" to "xyz"
        )

        openSearchKlient = OpenSearchKlient(miljøvariabler)
    }

    @Test
    fun `Responsen mappes korrekt når vi henter CV-er`() {
        val aktørId1 = "12345"
        val aktørId2 = "67890"
        val aktørIder = listOf(aktørId1, aktørId2)

        val openSearchRequestBody = openSearchKlient.lagBodyForHentingAvCver(aktørIder)
        val openSearchResponseBody = flereKandidaterFraES(aktørIder[0], aktørIder[1])

        stubHentingAvKandidater(
            requestBody = openSearchRequestBody,
            responsBody = openSearchResponseBody)

        val kandidaterMedCv: Map<String, Cv?> = openSearchKlient.hentCver(aktørIder)
        assertThat(kandidaterMedCv).hasSize(2)
        assertThat(kandidaterMedCv.values).hasSize(2)

        val cv1 = kandidaterMedCv[aktørId1]
        val cv2 = kandidaterMedCv[aktørId2]
        assertThat(cv1?.aktørId).isEqualTo(aktørId1)
        assertThat(cv2?.aktørId).isEqualTo(aktørId2)
        assertThat(cv1?.kompetanse).hasSize(22)
        assertThat(cv1?.kompetanse).containsAll(listOf("Fallskjermsertifikat",
            "Trenerarbeid",
            "Taekwondo",
            "Fritidsdykking",
            "Java (Programmeringsspråk)",
            "Kotlin (Programming Language)",
            "Scala",
            "Spring framework",
            "Java/JEE",
            "Relasjonsdatabase",
            "Git versjonskontrollsystem",
            "Apache Subversion (SVN)",
            "Jenkins (Software)",
            "Apache Maven",
            "Oracle Relational Database",
            "IntelliJ IDEA",
            "GitHub",
            "Agile-utvikling",
            "Agile Scrum",
            "Test-driven development (TDD)",
            "Domain Driven Design",
            "Kodegjennomgang"))
        assertThat(cv1?.arbeidserfaring).hasSize(3)
        assertThat(cv1?.arbeidserfaring?.get(0)?.arbeidsgiver).isEqualTo("Knowit Objectnet")
        assertThat(cv1?.utdanning).hasSize(3)
        assertThat(cv1?.utdanning?.get(0)?.utdannelsessted).isEqualTo("Universitetet i Oslo")
        assertThat(cv1?.språk).hasSize(5)
        assertThat(cv1?.språk?.get(0)?.navn).isEqualTo("Tysk")
        assertThat(cv1?.språk?.get(0)?.muntlig).isEqualTo("NYBEGYNNER")
        assertThat(cv1?.sammendrag).contains("Er fanatisk opptatt av religion, lever som en munk og synes at alle burde vite om vår Herre og Gud sin herlige nåde og fryd, priset være Herren, halleluja.")
        assertThat(cv1?.alder).isEqualTo(47)
        assertThat(cv1?.fornavn).isEqualTo("Ugjennomsiktig")
        assertThat(cv1?.etternavn).isEqualTo("Dal")
        assertThat(cv1?.bosted).isEqualTo("Vega")
        assertThat(cv1?.mobiltelefonnummer).isEqualTo(null)
    }

    @Test
    fun `hentCver returnerer nullverdi for kandidater som ikke finnes i OpenSearch`() {
        val aktørIder = listOf("12345", "67890")
        val openSearchRequestBody = openSearchKlient.lagBodyForHentingAvCver(aktørIder)
        val openSearchResponseBody = Testdata.ingenTreffKandidatOpensearchJson

        stubHentingAvKandidater(
            requestBody = openSearchRequestBody,
            responsBody = openSearchResponseBody
        )

        val kandidaterMedCv: Map<String, Cv?> = openSearchKlient.hentCver(aktørIder)

        assertThat(kandidaterMedCv).hasSize(2)
        assertThat(kandidaterMedCv.values).containsOnlyNulls()
    }


    private fun stubHentingAvKandidater(requestBody: String, responsBody: String) {
        wiremockServer.stubFor(post("/veilederkandidat_current/_search")
            .withBasicAuth("gunnar", "xyz")
            .withRequestBody(containing(requestBody))
            .willReturn(ok(responsBody)))
    }
 }