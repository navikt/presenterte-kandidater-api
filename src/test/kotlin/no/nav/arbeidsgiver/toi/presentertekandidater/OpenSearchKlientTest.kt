package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.tomakehurst.wiremock.client.WireMock.*
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.enKandidatFraESMedMangeNullFelter
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.flereKandidaterFraES
import no.nav.arbeidsgiver.toi.presentertekandidater.opensearch.Cv
import no.nav.arbeidsgiver.toi.presentertekandidater.opensearch.OpenSearchKlient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenSearchKlientTest {
    private lateinit var openSearchKlient: OpenSearchKlient
    private val wiremockServer = hentWiremock()

    @BeforeAll
    fun setUp() {
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
            responsBody = openSearchResponseBody
        )

        val kandidaterMedCv: Map<String, Cv?> = openSearchKlient.hentCver(aktørIder)
        assertThat(kandidaterMedCv).hasSize(2)
        assertThat(kandidaterMedCv.values).hasSize(2)

        val cv1 = kandidaterMedCv[aktørId1]
        val cv2 = kandidaterMedCv[aktørId2]
        assertThat(cv1?.aktørId).isEqualTo(aktørId1)
        assertThat(cv2?.aktørId).isEqualTo(aktørId2)
        assertThat(cv1?.kompetanse).hasSize(22)
        assertThat(cv1?.kompetanse).containsAll(
            listOf(
                "Fallskjermsertifikat",
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
                "Kodegjennomgang"
            )
        )
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
        assertThat(cv1?.mobiltelefonnummer).isEqualTo("44887766")
        assertThat(cv1?.førerkort?.first()?.førerkortKodeKlasse).isEqualTo("B - Personbil")
        assertThat(cv1?.fagdokumentasjon?.first()?.type).isEqualTo("Fagbrev/svennebrev")
        assertThat(cv2?.fagdokumentasjon?.size).isEqualTo(2)
    }

    @Test
    fun `Godta at gitte felter på CV er null`() {
        val aktørId = "12345"
        val openSearchRequestBody = openSearchKlient.lagBodyForHentingAvCver(listOf(aktørId))
        val openSearchResponseBody = enKandidatFraESMedMangeNullFelter(aktørId)

        stubHentingAvKandidater(
            requestBody = openSearchRequestBody,
            responsBody = openSearchResponseBody
        )

        val kandidaterMedCv: Map<String, Cv?> = openSearchKlient.hentCver(listOf(aktørId))
        assertThat(kandidaterMedCv).hasSize(1)
        assertThat(kandidaterMedCv.values).hasSize(1)
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

    @Test
    fun `hentAntallKandidater returnerer antall kandidater i OpenSearch`() {
        val forventetAntall = 193135L
        val forventetRespons = """
            {
            	"count": $forventetAntall,
            	"_shards": {
            		"total": 3,
            		"successful": 3,
            		"skipped": 0,
            		"failed": 0
            	}
            }
        """.trimIndent()

        wiremockServer.stubFor(
            get("/veilederkandidat_current/_count")
                .withBasicAuth("gunnar", "xyz")
                .willReturn(ok(forventetRespons))
        )

        val antallKandidater = openSearchKlient.hentAntallKandidater()
        assertThat(antallKandidater).isEqualTo(forventetAntall)
    }


    private fun stubHentingAvKandidater(requestBody: String, responsBody: String) {
        wiremockServer.stubFor(
            post("/veilederkandidat_current/_search")
                .withBasicAuth("gunnar", "xyz")
                .withRequestBody(containing(requestBody))
                .willReturn(ok(responsBody))
        )
    }
}