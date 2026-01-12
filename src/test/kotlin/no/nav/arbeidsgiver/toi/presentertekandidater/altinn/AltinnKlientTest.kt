package no.nav.arbeidsgiver.toi.presentertekandidater.altinn

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.http.Fault
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.TokendingsKlient
import org.junit.jupiter.api.*
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AltinnKlientTest {
    companion object {
        private lateinit var wireMockServer: WireMockServer
        private lateinit var altinnKlient: AltinnKlient
        private lateinit var tokendingsKlient: TokendingsKlient

        @BeforeAll
        @JvmStatic
        fun setup() {
            wireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
            wireMockServer.start()

            tokendingsKlient = mock(TokendingsKlient::class.java)
            `when`(
                tokendingsKlient.veksleInnToken(
                    "test-token",
                    "altinn-scope"
                )
            ).thenReturn("mocked-token")

            val testEnv = mapOf(
                "ALTINN_PROXY_URL" to "http://localhost:${wireMockServer.port()}/altinn-tilganger",
                "ALTINN_PROXY_AUDIENCE" to "din:app",
                "ALTINN_SCOPE" to "altinn-scope"
            )
            altinnKlient = AltinnKlient(testEnv, tokendingsKlient)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            wireMockServer.stop()
        }
    }

    @BeforeEach
    fun reset() {
        wireMockServer.resetAll()
        altinnKlient.tømCache()
    }

    @Test
    fun `Skal returnere organisasjoner ved OK respons fra Altinn`() {
        val accessToken = "test-token"
        val altinnResponseJson = """
            {
                "isError": false,
                "hierarki": [
                    {
                        "orgnr": "999888777",
                        "navn": "TESTORGANISASJON AS",
                        "altinn2Tilganger": [],
                        "altinn3Tilganger": ["nav_rekruttering_kandidater"],
                        "underenheter": [
                            {
                                "orgnr": "999888778",
                                "navn": "UNDERENHET AV TESTORGANISASJON AS",
                                "altinn2Tilganger": [],
                                "altinn3Tilganger": ["nav_rekruttering_kandidater"],
                                "underenheter": [],
                                "organisasjonsform": "AS",
                                "erSlettet": false
                            }
                        ],
                        "organisasjonsform": "AS",
                        "erSlettet": false
                    }
                ],
                "orgNrTilTilganger": {
                    "999888777": ["nav_rekruttering_kandidater"],
                    "999888778": ["nav_rekruttering_kandidater"]
                },
                "tilgangTilOrgNr": {
                    "nav_rekruttering_kandidater": ["999888777", "999888778"]
                }
            }
        """.trimIndent()

        wireMockServer.stubFor(
            WireMock.post("/altinn-tilganger")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(altinnResponseJson)
                )
        )

        val organisasjoner = altinnKlient.hentOrganisasjoner("12345678901", accessToken)
        Assertions.assertEquals(2, organisasjoner.size)
        Assertions.assertEquals("TESTORGANISASJON AS", organisasjoner[0].name)
        Assertions.assertEquals("999888777", organisasjoner[0].organizationNumber)
        wireMockServer.verify(
            1, WireMock.postRequestedFor(WireMock.urlEqualTo("/altinn-tilganger"))
        )

        val organisasjonerMedRettighetKandidater =
            altinnKlient.hentOrganisasjonerMedRettighetKandidaterFraAltinn("12345678901", accessToken)
        Assertions.assertEquals(1, organisasjonerMedRettighetKandidater.size)
        Assertions.assertEquals("UNDERENHET AV TESTORGANISASJON AS", organisasjonerMedRettighetKandidater[0].name)
        Assertions.assertEquals("999888778", organisasjonerMedRettighetKandidater[0].organizationNumber)
        wireMockServer.verify(
            2, WireMock.postRequestedFor(WireMock.urlEqualTo("/altinn-tilganger"))
        )
    }

    @Test
    fun `Skal godta rekrutteringsrettigheten i Altinn2`() {
        val accessToken = "test-token"
        val altinnResponseJson = """
            {
                "isError": false,
                "hierarki": [
                    {
                        "orgnr": "999888777",
                        "navn": "TESTORGANISASJON AS",
                        "altinn2Tilganger": [],
                        "altinn3Tilganger": [],
                        "underenheter": [
                            {
                                "orgnr": "999888778",
                                "navn": "UNDERENHET AV TESTORGANISASJON AS",
                                "altinn2Tilganger": ["5078:1"],
                                "altinn3Tilganger": [],
                                "underenheter": [],
                                "organisasjonsform": "AS",
                                "erSlettet": false
                            }
                        ],
                        "organisasjonsform": "AS",
                        "erSlettet": false
                    }
                ],
                "orgNrTilTilganger": {
                    "999888777": [],
                    "999888778": ["5078:1"]
                },
                "tilgangTilOrgNr": {
                    "5078:1": ["999888778"]
                }
            }
        """.trimIndent()

        wireMockServer.stubFor(
            WireMock.post("/altinn-tilganger")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(altinnResponseJson)
                )
        )

        val organisasjoner = altinnKlient.hentOrganisasjoner("12345678901", accessToken)
        Assertions.assertEquals(2, organisasjoner.size)
        Assertions.assertEquals("TESTORGANISASJON AS", organisasjoner[0].name)
        Assertions.assertEquals("999888777", organisasjoner[0].organizationNumber)
        wireMockServer.verify(
            1, WireMock.postRequestedFor(WireMock.urlEqualTo("/altinn-tilganger"))
        )

        val organisasjonerMedRettighetKandidater =
            altinnKlient.hentOrganisasjonerMedRettighetKandidaterFraAltinn("12345678901", accessToken)
        Assertions.assertEquals(1, organisasjonerMedRettighetKandidater.size)
        Assertions.assertEquals("UNDERENHET AV TESTORGANISASJON AS", organisasjonerMedRettighetKandidater[0].name)
        Assertions.assertEquals("999888778", organisasjonerMedRettighetKandidater[0].organizationNumber)
        wireMockServer.verify(
            2, WireMock.postRequestedFor(WireMock.urlEqualTo("/altinn-tilganger"))
        )
    }

    @Test
    fun `Skal kaste AltinnServiceException ved http 401 fra Altinn`() {
        val accessToken = "test-token"

        wireMockServer.stubFor(
            WireMock.post("/altinn-tilganger")
                .willReturn(
                    aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("Unauthorized")
                )
        )

        assertThrows<AltinnServiceException> {
            altinnKlient.hentOrganisasjoner("12345678901", accessToken)
        }

        assertThrows<AltinnServiceException> {
            altinnKlient.hentOrganisasjonerMedRettighetKandidaterFraAltinn("12345678901", accessToken)
        }
    }

    @Test
    fun `Skal prøve på nytt ved midlertidig feil fra Altinn`() {
        val accessToken = "test-token"

        wireMockServer.stubFor(
            WireMock.post("/altinn-tilganger")
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("Internal Server Error")
                )
        )

        // Sjekk at AltinnKlient prøver på nytt 3 ganger før den kaster exception ved `hentOrganisasjoner`
        assertThrows<AltinnServiceException> {
            altinnKlient.hentOrganisasjoner("12345678901", accessToken)
        }

        wireMockServer.verify(
            3,
            WireMock.postRequestedFor(WireMock.urlEqualTo("/altinn-tilganger"))
        )

        // Sjekk at AltinnKlient prøver på nytt nye 3 ganger før den kaster exception ved `hentOrganisasjonerMedRettighetKandidaterFraAltinn`
        assertThrows<AltinnServiceException> {
            altinnKlient.hentOrganisasjonerMedRettighetKandidaterFraAltinn("12345678901", accessToken)
        }

        wireMockServer.verify(
            6,
            WireMock.postRequestedFor(WireMock.urlEqualTo("/altinn-tilganger"))
        )
    }

    @Test
    fun `Skal ikke returnere organisasjoner når Altinn ikke returnerer noen tilganger`() {
        val accessToken = "test-token"
        val altinnResponseJson = """
            {
                "isError": false,
                "hierarki": [],
                "orgNrTilTilganger": {},
                "tilgangTilOrgNr": {}
            }
        """.trimIndent()

        wireMockServer.stubFor(
            WireMock.post("/altinn-tilganger")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(altinnResponseJson)
                )
        )

        val organisasjoner = altinnKlient.hentOrganisasjoner("12345678901", accessToken)
        Assertions.assertTrue(organisasjoner.isEmpty())

        val organisasjonerMedRettighetKandidater =
            altinnKlient.hentOrganisasjonerMedRettighetKandidaterFraAltinn("12345678901", accessToken)
        Assertions.assertTrue(organisasjonerMedRettighetKandidater.isEmpty())
    }

    @Test
    fun `Skal kaste AltinnException ved isError true og tomme tilgangsdata fra Altinn`() {
        val accessToken = "test-token"
        val altinnResponseJson = """
            {
                "isError": true,
                "hierarki": [],
                "orgNrTilTilganger": {},
                "tilgangTilOrgNr": {}
            }
        """.trimIndent()

        wireMockServer.stubFor(
            WireMock.post("/altinn-tilganger")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(altinnResponseJson)
                )
        )

        // Verifiser at det ble prøvd på nytt 3 ganger og deretter kastet exception
        assertThrows<AltinnException> {
            altinnKlient.hentOrganisasjoner("12345678901", accessToken)
        }
        wireMockServer.verify(
            3, WireMock.postRequestedFor(WireMock.urlEqualTo("/altinn-tilganger"))
        )

        // Verifiser at det ble prøvd på nytt 3 ganger og deretter kastet exception
        assertThrows<AltinnException> {
            altinnKlient.hentOrganisasjonerMedRettighetKandidaterFraAltinn("12345678901", accessToken)
        }
        wireMockServer.verify(
            6, WireMock.postRequestedFor(WireMock.urlEqualTo("/altinn-tilganger"))
        )
    }

    @Test
    fun `Skal kaste AltinnServiceException ved nettverksfeil mot Altinn`() {
        val accessToken = "test-token"
        wireMockServer.stubFor(
            WireMock.post("/altinn-tilganger")
                .willReturn(
                    aResponse()
                        .withFault(Fault.CONNECTION_RESET_BY_PEER)
                )
        )

        // Verifiser at det ble prøvd på nytt 3 ganger og deretter kastet exception
        assertThrows<AltinnException> {
            altinnKlient.hentOrganisasjoner("12345678901", accessToken)
        }
        wireMockServer.verify(
            3, WireMock.postRequestedFor(WireMock.urlEqualTo("/altinn-tilganger"))
        )

        // Verifiser at det ble prøvd på nytt 3 ganger og deretter kastet exception
        assertThrows<AltinnException> {
            altinnKlient.hentOrganisasjonerMedRettighetKandidaterFraAltinn("12345678901", accessToken)
        }
        wireMockServer.verify(
            6, WireMock.postRequestedFor(WireMock.urlEqualTo("/altinn-tilganger"))
        )
    }

    @Test
    fun `Skal mappe underenheter til sin overordnede enhet`() {
        val accessToken = "test-token"
        val overenhetOrgnr = "999888777"
        val underenhetOrgnr = "999888778"
        val altinnResponseJson = """
            {
                "isError": false,
                "hierarki": [
                    {
                        "orgnr": "$overenhetOrgnr",
                        "navn": "TESTORGANISASJON AS",
                        "altinn2Tilganger": [],
                        "altinn3Tilganger": [],
                        "underenheter": [
                            {
                                "orgnr": "$underenhetOrgnr",
                                "navn": "UNDERENHET AV TESTORGANISASJON AS",
                                "altinn2Tilganger": [],
                                "altinn3Tilganger": ["nav_rekruttering_kandidater"],
                                "underenheter": [],
                                "organisasjonsform": "AS",
                                "erSlettet": false
                            }
                        ],
                        "organisasjonsform": "AS",
                        "erSlettet": false
                    }
                ],
                "orgNrTilTilganger": {
                    "$underenhetOrgnr": ["nav_rekruttering_kandidater"]
                },
                "tilgangTilOrgNr": {
                    "nav_rekruttering_kandidater": ["$underenhetOrgnr"]
                }
            }
        """.trimIndent()

        wireMockServer.stubFor(
            WireMock.post("/altinn-tilganger")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(altinnResponseJson)
                )
        )

        val organisasjoner = altinnKlient.hentOrganisasjoner("12345678901", accessToken)
        Assertions.assertEquals(2, organisasjoner.size)
        Assertions.assertEquals(overenhetOrgnr, organisasjoner[0].organizationNumber)
        Assertions.assertNull(organisasjoner[0].parentOrganizationNumber)
        Assertions.assertEquals(underenhetOrgnr, organisasjoner[1].organizationNumber)
        Assertions.assertEquals(overenhetOrgnr, organisasjoner[1].parentOrganizationNumber)

        // Rettighet nav_rekruttering_kandidater mangler på overenhet, dermed skal kun underenheten med
        val organisasjonerMedRettighetKandidater =
            altinnKlient.hentOrganisasjonerMedRettighetKandidaterFraAltinn("12345678901", accessToken)
        Assertions.assertEquals(1, organisasjonerMedRettighetKandidater.size)
        Assertions.assertEquals(underenhetOrgnr, organisasjonerMedRettighetKandidater[0].organizationNumber)
        Assertions.assertEquals(overenhetOrgnr, organisasjonerMedRettighetKandidater[0].parentOrganizationNumber)
    }

    @Test
    fun `hentOrganisasjonerMedRettighetKandidaterFraAltinn skal kun returnere underenheter`() {
        val altinnResponseJson = """
        {
            "isError": false,
            "hierarki": [
                {
                    "orgnr": "111111111",
                    "navn": "OVERENHET MED RETTIGHET",
                    "altinn2Tilganger": [],
                    "altinn3Tilganger": ["nav_rekruttering_kandidater"],
                    "underenheter": [
                        {
                            "orgnr": "111111112",
                            "navn": "UNDERENHET MED RETTIGHET",
                            "altinn2Tilganger": [],
                            "altinn3Tilganger": ["nav_rekruttering_kandidater"],
                            "underenheter": [],
                            "organisasjonsform": "AS",
                            "erSlettet": false
                        }
                    ],
                    "organisasjonsform": "AS",
                    "erSlettet": false
                },
                {
                    "orgnr": "222222222",
                    "navn": "UTEN RETTIGHET",
                    "altinn2Tilganger": [],
                    "altinn3Tilganger": [],
                    "underenheter": [],
                    "organisasjonsform": "AS",
                    "erSlettet": false
                }
            ],
            "orgNrTilTilganger": {
                "111111111": ["nav_rekruttering_kandidater"],
                "111111112": ["nav_rekruttering_kandidater"],
                "222222222": []
            },
            "tilgangTilOrgNr": {
                "nav_rekruttering_kandidater": ["111111111", "111111112"]
            }
        }
    """.trimIndent()

        wireMockServer.stubFor(
            WireMock.post("/altinn-tilganger")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(altinnResponseJson)
                )
        )

        val result = altinnKlient.hentOrganisasjonerMedRettighetKandidaterFraAltinn("12345678901", "token")
        Assertions.assertEquals(1, result.size)
        Assertions.assertEquals("111111112", result[0].organizationNumber)
    }

    @Test
    fun `hentOrganisasjonerMedRettighetKandidaterFraAltinn skal kun returnere organisasjoner med riktig rettighet`() {
        val altinnResponseJson = """
        {
            "isError": false,
            "hierarki": [
                {
                    "orgnr": "111111111",
                    "navn": "OVERENHET MED RETTIGHET",
                    "altinn2Tilganger": [],
                    "altinn3Tilganger": ["nav_rekruttering_kandidater"],
                    "underenheter": [
                        {
                            "orgnr": "111111112",
                            "navn": "UNDERENHET MED RETTIGHET",
                            "altinn2Tilganger": [],
                            "altinn3Tilganger": ["nav_rekruttering_kandidater"],
                            "underenheter": [],
                            "organisasjonsform": "AS",
                            "erSlettet": false
                        }
                    ],
                    "organisasjonsform": "AS",
                    "erSlettet": false
                },
                {
                    "orgnr": "222222222",
                    "navn": "OVERENHET UTEN RETTIGHET",
                    "altinn2Tilganger": [],
                    "altinn3Tilganger": [],
                    "underenheter": [
                        {
                            "orgnr": "222222221",
                            "navn": "UNDERENHET UTEN RETTIGHET",
                            "altinn2Tilganger": [],
                            "altinn3Tilganger": [],
                            "underenheter": [],
                            "organisasjonsform": "AS",
                            "erSlettet": false
                        }
                    ],
                    "organisasjonsform": "AS",
                    "erSlettet": false
                }
            ],
            "orgNrTilTilganger": {
                "111111111": ["nav_rekruttering_kandidater"],
                "111111112": ["nav_rekruttering_kandidater"],
                "222222222": [],
                "222222221": []
            },
            "tilgangTilOrgNr": {
                "nav_rekruttering_kandidater": ["111111111", "111111112"]
            }
        }
    """.trimIndent()

        wireMockServer.stubFor(
            WireMock.post("/altinn-tilganger")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(altinnResponseJson)
                )
        )

        val result = altinnKlient.hentOrganisasjonerMedRettighetKandidaterFraAltinn("12345678901", "token")
        Assertions.assertEquals(1, result.size)
        Assertions.assertEquals("111111112", result[0].organizationNumber)
    }

    @Test
    fun `Skal ha separate cacher for ulike filter`() {
        val altinnResponseJson = """
        {
            "isError": false,
            "hierarki": [
                {
                    "orgnr": "999888777",
                    "navn": "TEST AS",
                    "altinn2Tilganger": [],
                    "altinn3Tilganger": ["nav_rekruttering_kandidater"],
                    "underenheter": [],
                    "organisasjonsform": "AS",
                    "erSlettet": false
                }
            ],
            "orgNrTilTilganger": {
                "999888777": ["nav_rekruttering_kandidater"]
            },
            "tilgangTilOrgNr": {
                "nav_rekruttering_kandidater": ["999888777"]
            }
        }
    """.trimIndent()

        wireMockServer.stubFor(
            WireMock.post("/altinn-tilganger")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(altinnResponseJson)
                )
        )

        altinnKlient.hentOrganisasjoner("12345678901", "token")
        altinnKlient.hentOrganisasjonerMedRettighetKandidaterFraAltinn("12345678901", "token")

        wireMockServer.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo("/altinn-tilganger")))
    }

}
