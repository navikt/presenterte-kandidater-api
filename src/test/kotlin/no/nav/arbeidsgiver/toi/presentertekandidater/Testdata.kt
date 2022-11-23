package no.nav.arbeidsgiver.toi.presentertekandidater


object Testdata {
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

    fun flereKandidaterFraES(aktørId1: String, aktørid2: String) =
        """
            {
            	"took": 3,
            	"timed_out": false,
            	"_shards": {
            		"total": 3,
            		"successful": 3,
            		"skipped": 0,
            		"failed": 0
            	},
            	"hits": {
            		"total": {
            			"value": 2,
            			"relation": "eq"
            		},
            		"max_score": 1.0,
            		"hits": [
            			{
            				"_index": "veilederkandidat_os4",
            				"_type": "_doc",
            				"_id": "PAM014lhcwy96",
            				"_score": 1.0,
            				"_source": {
            					"aktorId": "$aktørId1",
            					"utdanning": [
            						{
            							"yrkestatus": "INGEN",
            							"fraDato": "2001-07-31T22:00:00.000+00:00",
            							"utdannelsessted": "Universitetet i Oslo",
            							"tilDato": "2005-11-30T23:00:00.000+00:00",
            							"nusKode": "7",
            							"alternativGrad": "Master of Science, Informatikk",
            							"beskrivelse": "IT, programvareutvikling"
            						},
            						{
            							"yrkestatus": "INGEN",
            							"fraDato": "1997-07-31T22:00:00.000+00:00",
            							"utdannelsessted": "Universitetet i Oslo",
            							"tilDato": "2001-05-31T22:00:00.000+00:00",
            							"nusKode": "6",
            							"alternativGrad": "Cand.Mag., historie",
            							"beskrivelse": "Grunnfag i historie og idehistorie, grunnfag i tverrfaglig informatikk, semesteremne i kinesisk"
            						},
            						{
            							"yrkestatus": "INGEN",
            							"fraDato": "1991-07-31T22:00:00.000+00:00",
            							"utdannelsessted": "Ytre Namdal videregående skole",
            							"tilDato": "1994-05-31T22:00:00.000+00:00",
            							"nusKode": "4",
            							"alternativGrad": "Allmenne fag",
            							"beskrivelse": "Tysk, fransk, elevrådsarbeid, samfunnskunnskap, geografi, religion"
            						}
            					],
            					"kompetanseObj": [
            						{
            							"alternativtNavn": "Fallskjermsertifikat",
            							"kompKode": null,
            							"kompKodeNavn": "Fallskjermsertifikat",
            							"sokeNavn": [
            								"Fallskjermsertifikat",
            								"Fallskjermsertifikat",
            								"Ekstremsport"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Trenerarbeid",
            							"kompKode": null,
            							"kompKodeNavn": "Trenerarbeid",
            							"sokeNavn": [
            								"Trenerarbeid",
            								"Trenerarbeid",
            								"Trener/ instruktørbakgrunn"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Taekwondo",
            							"kompKode": null,
            							"kompKodeNavn": "Taekwondo",
            							"sokeNavn": [
            								"Taekwondo"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Fritidsdykking",
            							"kompKode": null,
            							"kompKodeNavn": "Fritidsdykking",
            							"sokeNavn": [
            								"Fritidsdykking",
            								"Dykking (gi opplæring)",
            								"Fritidsdykking",
            								"Dykking",
            								"Utøve sport",
            								"Idrettstrening",
            								"Fysiske og atletiske ferdigheter"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Java (Programmeringsspråk)",
            							"kompKode": null,
            							"kompKodeNavn": "Java (Programmeringsspråk)",
            							"sokeNavn": [
            								"Java (Programmeringsspråk)",
            								"Java (Programmeringsspråk)",
            								"Objektorientert modellering",
            								"Operativsystem",
            								"Operating System (ICT)",
            								"Objektorientert programmering",
            								"Software Platform"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Kotlin (Programming Language)",
            							"kompKode": null,
            							"kompKodeNavn": "Kotlin (Programming Language)",
            							"sokeNavn": [
            								"Kotlin (Programming Language)",
            								"Kotlin",
            								"Java (Programmeringsspråk)"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Scala",
            							"kompKode": null,
            							"kompKodeNavn": "Scala",
            							"sokeNavn": [
            								"Scala",
            								"Scala"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Spring framework",
            							"kompKode": null,
            							"kompKodeNavn": "Spring framework",
            							"sokeNavn": [
            								"Spring framework",
            								"Spring Framework (Java)",
            								"Software Framework (ICT)",
            								"Java (Programmeringsspråk)",
            								"Objektorientert modellering",
            								"Javascript Framework",
            								"JavaScript rammeverk"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Java/JEE",
            							"kompKode": null,
            							"kompKodeNavn": "Java/JEE",
            							"sokeNavn": [
            								"Java/JEE",
            								"Java (Programmeringsspråk)",
            								"Objektorientert modellering",
            								"Operativsystem",
            								"Operating System (ICT)",
            								"Objektorientert programmering",
            								"Software Platform"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Relasjonsdatabase",
            							"kompKode": null,
            							"kompKodeNavn": "Relasjonsdatabase",
            							"sokeNavn": [
            								"Relasjonsdatabase",
            								"Databasesystemer",
            								"Database"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Git versjonskontrollsystem",
            							"kompKode": null,
            							"kompKodeNavn": "Git versjonskontrollsystem",
            							"sokeNavn": [
            								"Git versjonskontrollsystem",
            								"Git (Versioning File System)",
            								"Git versjonskontrollsystem"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Apache Subversion (SVN)",
            							"kompKode": null,
            							"kompKodeNavn": "Apache Subversion (SVN)",
            							"sokeNavn": [
            								"Apache Subversion (SVN)",
            								"Apache Subversion (SVN)"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Jenkins (Software)",
            							"kompKode": null,
            							"kompKodeNavn": "Jenkins (Software)",
            							"sokeNavn": [
            								"Jenkins (Software)",
            								"Jenkins (Software)",
            								"Enterprise Application Integration (EAI)",
            								"Kontinuerlig integrasjon",
            								"Java/JEE",
            								"Java EE (Java Enterprise Edition)"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Apache Maven",
            							"kompKode": null,
            							"kompKodeNavn": "Apache Maven",
            							"sokeNavn": [
            								"Apache Maven",
            								"Apache Maven"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Oracle Relational Database",
            							"kompKode": null,
            							"kompKodeNavn": "Oracle Relational Database",
            							"sokeNavn": [
            								"Oracle Relational Database",
            								"Oracle Relational Database"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "IntelliJ IDEA",
            							"kompKode": null,
            							"kompKodeNavn": "IntelliJ IDEA",
            							"sokeNavn": [
            								"IntelliJ IDEA",
            								"IntelliJ IDEA",
            								"IntelliJ",
            								"Java (Programmeringsspråk)"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "GitHub",
            							"kompKode": null,
            							"kompKodeNavn": "GitHub",
            							"sokeNavn": [
            								"GitHub",
            								"Git (Versioning File System)",
            								"Git versjonskontrollsystem",
            								"Kontinuerlig integrasjon"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Agile-utvikling",
            							"kompKode": null,
            							"kompKodeNavn": "Agile-utvikling",
            							"sokeNavn": [
            								"Agile-utvikling",
            								"Agile-utvikling",
            								"Iterativ utvikling",
            								"Interativ designmetode",
            								"Agile-prosjektledelse",
            								"Agile Methodologies (prosjektledelse)"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Agile Scrum",
            							"kompKode": null,
            							"kompKodeNavn": "Agile Scrum",
            							"sokeNavn": [
            								"Agile Scrum",
            								"Agile Scrum",
            								"Agile-utvikling",
            								"Prosjektstyringsmetodikk"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Test-driven development (TDD)",
            							"kompKode": null,
            							"kompKodeNavn": "Test-driven development (TDD)",
            							"sokeNavn": [
            								"Test-driven development (TDD)",
            								"Testdrevet utvikling",
            								"Test-driven development (TDD)",
            								"Agile-utvikling"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Domain Driven Design",
            							"kompKode": null,
            							"kompKodeNavn": "Domain Driven Design",
            							"sokeNavn": [
            								"Domain Driven Design",
            								"Programvaredesign"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						},
            						{
            							"alternativtNavn": "Kodegjennomgang",
            							"kompKode": null,
            							"kompKodeNavn": "Kodegjennomgang",
            							"sokeNavn": [
            								"Kodegjennomgang",
            								"Kodegjennomgang",
            								"Programvaretesting",
            								"Kodeskriving"
            							],
            							"fraDato": null,
            							"beskrivelse": ""
            						}
            					],
            					"fornavn": "Ugjennomsiktig",
            					"poststed": "Vega",
            					"mobiltelefon": null,
            					"fodselsdato": "1975-09-19T23:00:00.000+00:00",
            					"etternavn": "Dal",
            					"epostadresse": null,
            					"sprak": [
            						{
            							"sprakKode": null,
            							"fraDato": null,
            							"sprakKodeTekst": "Tysk",
            							"ferdighetSkriftlig": "NYBEGYNNER",
            							"alternativTekst": "Tysk",
            							"beskrivelse": "Muntlig: NYBEGYNNER Skriftlig: NYBEGYNNER",
            							"ferdighetMuntlig": "NYBEGYNNER"
            						},
            						{
            							"sprakKode": null,
            							"fraDato": null,
            							"sprakKodeTekst": "Engelsk",
            							"ferdighetSkriftlig": "VELDIG_GODT",
            							"alternativTekst": "Engelsk",
            							"beskrivelse": "Muntlig: VELDIG_GODT Skriftlig: VELDIG_GODT",
            							"ferdighetMuntlig": "VELDIG_GODT"
            						},
            						{
            							"sprakKode": null,
            							"fraDato": null,
            							"sprakKodeTekst": "Fransk",
            							"ferdighetSkriftlig": "NYBEGYNNER",
            							"alternativTekst": "Fransk",
            							"beskrivelse": "Muntlig: NYBEGYNNER Skriftlig: NYBEGYNNER",
            							"ferdighetMuntlig": "NYBEGYNNER"
            						},
            						{
            							"sprakKode": null,
            							"fraDato": null,
            							"sprakKodeTekst": "Kinesisk",
            							"ferdighetSkriftlig": "NYBEGYNNER",
            							"alternativTekst": "Kinesisk",
            							"beskrivelse": "Muntlig: NYBEGYNNER Skriftlig: NYBEGYNNER",
            							"ferdighetMuntlig": "NYBEGYNNER"
            						},
            						{
            							"sprakKode": null,
            							"fraDato": null,
            							"sprakKodeTekst": "Norsk",
            							"ferdighetSkriftlig": "FOERSTESPRAAK",
            							"alternativTekst": "Norsk",
            							"beskrivelse": "Muntlig: FOERSTESPRAAK Skriftlig: FOERSTESPRAAK",
            							"ferdighetMuntlig": "FOERSTESPRAAK"
            						}
            					],
            					"yrkeserfaring": [
            						{
            							"styrkKode3Siffer": "251",
            							"stillingstittel": "IT-konsulent",
            							"sokeTitler": [
            								"Rådgiver",
            								"IKT-konsulent",
            								"Konsulent",
            								"Selvstendig konsulent",
            								"Fagkonsulent",
            								"Rådgiver IT",
            								"IT-konsulent"
            							],
            							"alternativStillingstittel": "Utvikler",
            							"styrkKode4Siffer": "2511",
            							"stillingstitlerForTypeahead": [
            								"IKT-konsulent",
            								"Rådgiver IT",
            								"IT-konsulent"
            							],
            							"arbeidsgiver": "Knowit Objectnet",
            							"fraDato": "2015-03-31T22:00:00.000+00:00",
            							"sted": "Oslo",
            							"yrkeserfaringManeder": 34,
            							"naceKode": null,
            							"organisasjonsnummer": null,
            							"tilDato": "2018-01-31T23:00:00.000+00:00",
            							"styrkKode": "2511",
            							"utelukketForFremtiden": false,
            							"beskrivelse": "IT-konsulent, utvikler, Java og Scala. Utleid til to ulike prosjekter, begge i Oslo kommune hele perioden"
            						},
            						{
            							"styrkKode3Siffer": null,
            							"stillingstittel": "Utvikler (Frontend- og backend)",
            							"sokeTitler": [
            								"Systemerer",
            								"Utvikler (Frontend- og backend)",
            								"Systemutvikler"
            							],
            							"alternativStillingstittel": "Utvikler (Frontend- og backend)",
            							"styrkKode4Siffer": null,
            							"stillingstitlerForTypeahead": [
            								"Utvikler (Frontend- og backend)"
            							],
            							"arbeidsgiver": "Skattedirektoratet",
            							"fraDato": "2006-07-31T22:00:00.000+00:00",
            							"sted": "Oslo",
            							"yrkeserfaringManeder": 104,
            							"naceKode": null,
            							"organisasjonsnummer": null,
            							"tilDato": "2015-03-31T22:00:00.000+00:00",
            							"styrkKode": "",
            							"utelukketForFremtiden": false,
            							"beskrivelse": "Programmering, videreutvikling og forvalting av Skatteetatens intranettsted. Utvikler i flere utviklingsprosjekter for å lage nye systemer, basert på Java Enterprise Edition (JEE 6)."
            						},
            						{
            							"styrkKode3Siffer": "522",
            							"stillingstittel": "Kioskarbeider",
            							"sokeTitler": [
            								"Kioskarbeider",
            								"Butikkonsulent",
            								"Kioskmedarbeider",
            								"Torghandler"
            							],
            							"alternativStillingstittel": "Kioskarbeider",
            							"styrkKode4Siffer": "5223",
            							"stillingstitlerForTypeahead": [
            								"Kioskarbeider",
            								"Kioskmedarbeider"
            							],
            							"arbeidsgiver": "Narvesen St.hanshaugen",
            							"fraDato": "1998-02-28T23:00:00.000+00:00",
            							"sted": "Oslo",
            							"yrkeserfaringManeder": 54,
            							"naceKode": null,
            							"organisasjonsnummer": null,
            							"tilDato": "2002-08-31T22:00:00.000+00:00",
            							"styrkKode": "5223",
            							"utelukketForFremtiden": false,
            							"beskrivelse": "Salg, kundebehandling, ansvar for hele kiosken fra og med åpning til og med stenging.\nDeltid 2 dagsverk i uken ved siden av fulltidsstudier."
            						}
            					],
            					"beskrivelse": "It-utvikler, backend-programmerer på JVM (Java-økosystemet). Fokus på kodekvalitet. God på modellering og å trekke essensen ut av en problemstillg. Er fanatisk opptatt av religion, lever som en munk og synes at alle burde vite om vår Herre og Gud sin herlige nåde og fryd, priset være Herren, halleluja."
            				}
            			},
            			{
            				"_index": "veilederkandidat_os4",
            				"_type": "_doc",
            				"_id": "PAM019w4pxbus",
            				"_score": 1.0,
            				"_source": {
            					"aktorId": "$aktørid2",
            					"utdanning": [],
            					"kompetanseObj": [],
            					"fornavn": "Elastisk",
            					"poststed": "Svolvær",
            					"mobiltelefon": null,
            					"fodselsdato": "1970-12-14T23:00:00.000+00:00",
            					"etternavn": "Vaktel",
            					"epostadresse": null,
            					"sprak": [],
            					"yrkeserfaring": [],
            					"beskrivelse": ""
            				}
            			}
            		]
            	}
            }
        """.trimIndent()

    fun aktørIdFraOpenSearch(aktørId: String) = """
        {
        	"took": 1,
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
        		"max_score": 10.977694,
        		"hits": [
        			{
        				"_index": "veilederkandidat_os4",
        				"_type": "_doc",
        				"_id": "AF611502",
        				"_score": 10.977694,
        				"fields": {
        					"aktorId": [
        						"$aktørId"
        					]
        				}
        			}
        		]
        	}
        }
    """.trimIndent()
    }
