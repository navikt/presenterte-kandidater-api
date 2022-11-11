package no.nav.arbeidsgiver.toi.presentertekandidater

import org.junit.jupiter.api.Assertions.*

 class ESClientTest {




     val esKandidatUrl = "https://opensearch-toi-kandidat-nav-dev.aivencloud.com:26482/veilederkandidat_current/_search?q=aktorId:2890002595053"

     val esKandidatJson = """
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
         					"aktorId": "2890002595053",
         					"fodselsnummer": "15927099516",
         					"fornavn": "Elastisk",
         					"etternavn": "Vaktel",
         					"fodselsdato": "1970-12-14T23:00:00.000+00:00",
         					"fodselsdatoErDnr": false,
         					"formidlingsgruppekode": "ARBS",
         					"epostadresse": null,
         					"mobiltelefon": null,
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
 }