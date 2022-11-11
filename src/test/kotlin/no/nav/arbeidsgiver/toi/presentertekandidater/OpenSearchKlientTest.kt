package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenSearchKlientTest {

    private val wiremockServer = WireMockServer()
    private lateinit var openSearchKlient: OpenSearchKlient

    @BeforeAll
    fun setUp() {
        wiremockServer.start()
        val miljøvariabler = mapOf(
            "OPENSEARCH_URL" to "http://localhost:${wiremockServer.port()}",
            "OPENSEARCH_USERNAME" to "gunnar",
            "OPENSEARCH_PASSWORD" to "xyz"
        )
        openSearchKlient = OpenSearchKlient(miljøvariabler)

    }

    @Test
    fun `Skal mappe respons fra OpenSearch korrekt når vi henter én kandidat`() {
        val aktørId = "12345"
        val fornavn = "Fin"
        val etternavn = "Fugl"
        val esRepons = esKandidatJson(aktørId = aktørId, fornavn = fornavn, etternavn = etternavn)
        stubHentingAvEnKandidat(aktørId = aktørId, responsBody = esRepons)

        val kandidat = openSearchKlient.hentKandiat(aktørId)

        assertNotNull(kandidat)
        assertThat(kandidat.aktørId).isEqualTo(aktørId)
        assertThat(kandidat.fornavn).isEqualTo(fornavn)
        assertThat(kandidat.etternavn).isEqualTo(etternavn)
        assertThat(kandidat.bosted).isEqualTo("Svolvær")
        assertThat(kandidat.epost).isEqualTo("hei@hei.no")
        assertThat(kandidat.mobiltelefonnummer).isEqualTo("99887766")

        // Resten som trengs i CV-visning
    }

    @Test
    fun `hentKandiat skal returnere null når kandidat ikke finnes`() {
        val aktørId = "12345"
        stubHentingAvEnKandidat(aktørId = aktørId, responsBody = ingenTreffKandidatOpensearchJson)

        val kandidat = openSearchKlient.hentKandiat(aktørId)
        assertThat(kandidat).isNull()
    }

    fun stubHentingAvEnKandidat(aktørId: String, responsBody: String) {
        wiremockServer.stubFor(get("/veilederkandidat_current/_search?q=aktorId:$aktørId")
            .withBasicAuth("gunnar", "xyz")
            .willReturn(ok(responsBody)))
    }

     fun esKandidatJson(aktørId: String, fornavn: String, etternavn: String) = """
         {
         	"took": 78,
         	"timed_out": false,
         	"_shards": {
         		"total": 3,
         		"successful": 3,
         		"skipped": 0,
         		"failed": 0
         	},
         	"hits": {
         		"total": {
         			"value": 1,
         			"relation": "eq"
         		},
         		"max_score": 2.3671236,
         		"hits": [
         			{
         				"_index": "veilederkandidat_os4",
         				"_type": "_doc",
         				"_id": "PAM019w4pxbus",
         				"_score": 2.3671236,
         				"_source": {
         					"aktorId": "$aktørId",
         					"fodselsnummer": "15927099516",
         					"fornavn": "$fornavn",
         					"etternavn": "$etternavn",
         					"fodselsdato": "1970-12-14T23:00:00.000+00:00",
         					"fodselsdatoErDnr": false,
         					"formidlingsgruppekode": "ARBS",
         					"epostadresse": "hei@hei.no",
         					"mobiltelefon": "99887766",
         					"harKontaktinformasjon": false,
         					"telefon": null,
         					"statsborgerskap": "",
         					"kandidatnr": "PAM019w4pxbus",
         					"arenaKandidatnr": "PAM019w4pxbus",
         					"beskrivelse": "",
         					"samtykkeStatus": "G",
         					"samtykkeDato": "2022-09-26T11:02:19.387+00:00",
         					"adresselinje1": "Skipperveien 16",
         					"adresselinje2": "",
         					"adresselinje3": "",
         					"postnummer": "8300",
         					"poststed": "Svolvær",
         					"landkode": null,
         					"kommunenummer": 1865,
         					"kommunenummerkw": 1865,
         					"kommunenummerstring": "1865",
         					"fylkeNavn": "Nordland",
         					"kommuneNavn": "Vågan",
         					"disponererBil": false,
         					"tidsstempel": "2022-09-26T11:02:19.387+00:00",
         					"doed": false,
         					"frKode": "0",
         					"kvalifiseringsgruppekode": "BATT",
         					"hovedmaalkode": "SKAFFEA",
         					"orgenhet": "1860",
         					"navkontor": "NAV Lofoten",
         					"fritattKandidatsok": null,
         					"fritattAgKandidatsok": null,
         					"utdanning": [],
         					"fagdokumentasjon": [],
         					"yrkeserfaring": [],
         					"kompetanseObj": [],
         					"annenerfaringObj": [],
         					"sertifikatObj": [],
         					"forerkort": [],
         					"sprak": [],
         					"kursObj": [],
         					"vervObj": [],
         					"geografiJobbonsker": [
         						{
         							"geografiKodeTekst": "Oslo",
         							"geografiKode": "NO03"
         						}
         					],
         					"yrkeJobbonskerObj": [
         						{
         							"styrkKode": null,
         							"styrkBeskrivelse": "Kokkelærling",
         							"sokeTitler": [
         								"Kokkelærling",
         								"Kokkelærling",
         								"Kokk",
         								"Kafekokk",
         								"Lærlingplass"
         							],
         							"primaertJobbonske": false
         						}
         					],
         					"omfangJobbonskerObj": [
         						{
         							"omfangKode": "DELTID",
         							"omfangKodeTekst": "Deltid"
         						}
         					],
         					"ansettelsesformJobbonskerObj": [
         						{
         							"ansettelsesformKode": "ENGASJEMENT",
         							"ansettelsesformKodeTekst": "Engasjement"
         						}
         					],
         					"arbeidstidsordningJobbonskerObj": [],
         					"arbeidsdagerJobbonskerObj": [],
         					"arbeidstidJobbonskerObj": [
         						{
         							"arbeidstidKode": "DAGTID",
         							"arbeidstidKodeTekst": "Dagtid"
         						}
         					],
         					"samletKompetanseObj": [],
         					"totalLengdeYrkeserfaring": 0,
         					"synligForArbeidsgiverSok": false,
         					"synligForVeilederSok": true,
         					"oppstartKode": "LEDIG_NAA",
         					"veileder": null,
         					"inkluderingsbehov": false,
         					"tilretteleggingsbehov": false,
         					"godkjenninger": [],
         					"perioderMedInaktivitet": {
         						"startdatoForInnevarendeInaktivePeriode": null,
         						"sluttdatoerForInaktivePerioderPaToArEllerMer": []
         					},
         					"veilTilretteleggingsbehov": []
         				}
         			}
         		]
         	}
         }
     """.trimIndent()

    val ingenTreffKandidatOpensearchJson =
        """
            {
            	"took": 5,
            	"timed_out": false,
            	"_shards": {
            		"total": 3,
            		"successful": 3,
            		"skipped": 0,
            		"failed": 0
            	},
            	"hits": {
            		"total": {
            			"value": 0,
            			"relation": "eq"
            		},
            		"max_score": null,
            		"hits": []
            	}
            }
        """.trimIndent()
 }