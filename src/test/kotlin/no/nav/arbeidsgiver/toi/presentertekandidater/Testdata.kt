package no.nav.arbeidsgiver.toi.presentertekandidater

import java.time.ZonedDateTime

class Testdata {
    companion object {
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
                      "fodselsdato": "${ZonedDateTime.now().minusYears(40)}",
                      "fodselsdatoErDnr": false,
                      "formidlingsgruppekode": "ARBS",
                      "epostadresse": "hei@hei.no",
                      "mobiltelefon": "99887766",
                      "harKontaktinformasjon": false,
                      "telefon": null,
                      "statsborgerskap": "",
                      "kandidatnr": "PAM019w4pxbus",
                      "arenaKandidatnr": "PAM019w4pxbus",
                      "beskrivelse": "Dette er et sammendrag.",
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
                      "utdanning": [
                        {
                          "fraDato": "2019-07-31T22:00:00.000+00:00",
                          "tilDato": "2021-05-31T22:00:00.000+00:00",
                          "utdannelsessted": "NHH",
                          "nusKode": "7",
                          "alternativGrad": "Master markedsføring",
                          "yrkestatus": "INGEN",
                          "beskrivelse": "Markedsføring"
                        },
                        {
                          "fraDato": "1997-05-31T22:00:00.000+00:00",
                          "tilDato": "2000-05-31T22:00:00.000+00:00",
                          "utdannelsessted": "Lovisenberg høyskole",
                          "nusKode": "6",
                          "alternativGrad": "Bachelor i Sykepleie",
                          "yrkestatus": "INGEN",
                          "beskrivelse": "Sykepleie"
                        }
                      ],
                      "fagdokumentasjon": [],
                      "yrkeserfaring": [
                        {
                          "fraDato": "2018-06-30T22:00:00.000+00:00",
                          "tilDato": "2022-04-30T22:00:00.000+00:00",
                          "arbeidsgiver": "Stormote AS",
                          "styrkKode": "5223",
                          "styrkKode4Siffer": "5223",
                          "styrkKode3Siffer": "522",
                          "stillingstittel": "Butikkmedarbeider klesbutikk",
                          "stillingstitlerForTypeahead": [
                            "Selger motebutikk",
                            "Butikkmedarbeider klesbutikk",
                            "Konfeksjonsselger"
                          ],
                          "alternativStillingstittel": "Butikkmedarbeider klesbutikk",
                          "sokeTitler": [
                            "Selger motebutikk",
                            "Salgsassistent",
                            "Provisjonsselger",
                            "Juniorselger",
                            "Salg- og Kundeservicemedarbeider",
                            "Butikkmedarbeider klesbutikk",
                            "Salgskraft",
                            "Konfeksjonsselger",
                            "Salgskonsulent",
                            "Salgsrådgiver",
                            "Salgsmedarbeider",
                            "Salg - Kundebehandler",
                            "Salgs- og kunderådgiver",
                            "Kundeservicemedarbeider (salg)",
                            "Rådgivende selger",
                            "Salgsspesialist",
                            "Salgsperson",
                            "Selger"
                          ],
                          "organisasjonsnummer": null,
                          "naceKode": null,
                          "yrkeserfaringManeder": 46,
                          "utelukketForFremtiden": false,
                          "beskrivelse": "Jobb i butikk som drev med både klær og tekstil. Som grossist og til privatkunder.",
                          "sted": "Oslo"
                        },
                        {
                          "fraDato": "2015-04-30T22:00:00.000+00:00",
                          "tilDato": "2018-02-28T23:00:00.000+00:00",
                          "arbeidsgiver": "H&M Storo",
                          "styrkKode": "5223",
                          "styrkKode4Siffer": "5223",
                          "styrkKode3Siffer": "522",
                          "stillingstittel": "Butikkmedarbeider klesbutikk",
                          "stillingstitlerForTypeahead": [
                            "Selger motebutikk",
                            "Butikkmedarbeider klesbutikk",
                            "Konfeksjonsselger"
                          ],
                          "alternativStillingstittel": "Butikkmedarbeider klesbutikk",
                          "sokeTitler": [
                            "Selger motebutikk",
                            "Salgsassistent",
                            "Provisjonsselger",
                            "Juniorselger",
                            "Salg- og Kundeservicemedarbeider",
                            "Butikkmedarbeider klesbutikk",
                            "Salgskraft",
                            "Konfeksjonsselger",
                            "Salgskonsulent",
                            "Salgsrådgiver",
                            "Salgsmedarbeider",
                            "Salg - Kundebehandler",
                            "Salgs- og kunderådgiver",
                            "Kundeservicemedarbeider (salg)",
                            "Rådgivende selger",
                            "Salgsspesialist",
                            "Salgsperson",
                            "Selger"
                          ],
                          "organisasjonsnummer": null,
                          "naceKode": null,
                          "yrkeserfaringManeder": 34,
                          "utelukketForFremtiden": false,
                          "beskrivelse": "Ordinær ansatt i klesbutikk. Hadde behov for å trappe ned etter en stressende stilling som daglig leder hos Carlings. Generelt butikkarbeid, salg.",
                          "sted": "Oslo"
                        }
                      ],
                      "kompetanseObj": [
                        {
                          "fraDato": null,
                          "kompKode": null,
                          "kompKodeNavn": "Sykepleievitenskap",
                          "sokeNavn": [
                            "Sykepleievitenskap",
                            "Sykepleievitenskap",
                            "Helsefag",
                            "Implementere omsorgtjenester"
                          ],
                          "alternativtNavn": "Sykepleievitenskap",
                          "beskrivelse": ""
                        },
                        {
                          "fraDato": null,
                          "kompKode": null,
                          "kompKodeNavn": "Markedsanalyse",
                          "sokeNavn": [
                            "Markedsanalyse",
                            "Markedsanalyse",
                            "Markedsføringsforskning"
                          ],
                          "alternativtNavn": "Markedsanalyse",
                          "beskrivelse": ""
                        }
                      ],
                      "annenerfaringObj": [],
                      "sertifikatObj": [],
                      "forerkort": [],
                      "sprak": [
                        {
                          "fraDato": null,
                          "sprakKode": null,
                          "sprakKodeTekst": "Engelsk",
                          "alternativTekst": "Engelsk",
                          "beskrivelse": "Muntlig: VELDIG_GODT Skriftlig: GODT",
                          "ferdighetMuntlig": "VELDIG_GODT",
                          "ferdighetSkriftlig": "GODT"
                        },
                        {
                          "fraDato": null,
                          "sprakKode": null,
                          "sprakKodeTekst": "Norsk",
                          "alternativTekst": "Norsk",
                          "beskrivelse": "Muntlig: FOERSTESPRAAK Skriftlig: FOERSTESPRAAK",
                          "ferdighetMuntlig": "FOERSTESPRAAK",
                          "ferdighetSkriftlig": "FOERSTESPRAAK"
                        }
                      ],
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
                        },
                        {
                          "styrkKode": null,
                          "styrkBeskrivelse": "Skipskokk",
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
                      "totalLengdeYrkeserfaring": 33,
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
}