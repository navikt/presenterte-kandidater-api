package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import java.math.BigInteger
import java.time.ZonedDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ControllerTest {

    private val mockOAuth2Server = MockOAuth2Server()
    private val javalin = opprettJavalinMedTilgangskontroll(issuerProperties)
    private val repository = opprettTestRepositoryMedLokalPostgres()
    private val wiremockServer = WireMockServer(8888)

    @BeforeAll
    fun init() {
        mockOAuth2Server.start(port = 18301)
        wiremockServer.start()
        val miljøvariabler = mapOf(
            "OPEN_SEARCH_URI" to "http://localhost:${wiremockServer.port()}",
            "OPEN_SEARCH_USERNAME" to "gunnar",
            "OPEN_SEARCH_PASSWORD" to "xyz"
        )
        val openSearchKlient = OpenSearchKlient(miljøvariabler)
        startLocalApplication(javalin = javalin, repository = repository, openSearchKlient = openSearchKlient)

    }

    @AfterAll
    fun cleanUp() {
        mockOAuth2Server.shutdown()
        javalin.stop()
    }

    @Test
    fun `GET mot kandidatlister-endepunkt gir httpstatus 403 uten et gyldig token`() {
        val (_, response) = Fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=123")
            .authentication().bearer(hentUgyldigToken(mockOAuth2Server))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `GET mot kandidater-endepunkt gir httpstatus 403 uten token`() {
        val (_, response) = Fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=123")
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `GET mot kandidaterliste med kandidater gir status 200`() {
        val aktørId = "1234"

        val esRepons = Testdata.esKandidatJson(aktørId = aktørId, fornavn = "Per", etternavn = "Person")
        stubHentingAvEnKandidat(aktørId = aktørId, responsBody = esRepons)

        val stillingId = UUID.fromString("4bd2c240-92d2-4166-ac54-ba3d21bfbc07")
        repository.lagre(kandidatliste().copy(stillingId = stillingId))
        val kandidatliste = repository.hentKandidatliste(stillingId)
        repository.lagre(
            Kandidat(
                aktørId = aktørId,
                kandidatlisteId = kandidatliste?.id!!,
                uuid = UUID.fromString("28e2c1f6-dea5-46d1-90cd-bfbd994e06df")
            )
        )
        val (_, response) = Fuel
            .get("http://localhost:9000/kandidatliste/$stillingId")
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        val jsonbody = response.body().asString("application/json;charset=utf-8")
        val kandidatlisteFromJson = defaultObjectMapper.readTree(jsonbody)
        val opprettetDato = kandidatlisteFromJson["kandidatliste"]["opprettet"].asText()
        Assertions.assertThat(ZonedDateTime.parse(opprettetDato))
            .isBefore(ZonedDateTime.now()).isAfter(ZonedDateTime.now().minusMinutes(1))
        Assertions.assertThat(jsonbody)
            .isEqualToIgnoringWhitespace(("""
                {
                  "kandidatliste": {
                    "uuid": "7ea380f8-a0af-433f-8cbc-51c5788a7d29",
                    "stillingId": "4bd2c240-92d2-4166-ac54-ba3d21bfbc07",
                    "tittel": "Tittel",
                    "status": "ÅPEN",
                    "slettet": false,
                    "virksomhetsnummer": "123456789",
                    "sistEndret": "2022-11-15T14:46:39.051+01:00",
                    "opprettet": "${opprettetDato}"
                  },
                  "kandidater": [
                    {
                      "kandidat": {
                        "uuid": "28e2c1f6-dea5-46d1-90cd-bfbd994e06df"
                      },
                      "cv": {
                        "fornavn": "Per",
                        "etternavn": "Person",
                        "bosted": "Svolvær",
                        "mobiltelefonnummer": "99887766",
                        "epost": "hei@hei.no",
                        "alder": 40,
                        "kompetanse": [
                          "Sykepleievitenskap",
                          "Markedsanalyse"
                        ],
                        "arbeidserfaring": [
                          {
                            "fraDato": "2018-07-01T00:00:00+02:00",
                            "tilDato": "2022-05-01T00:00:00+02:00",
                            "arbeidsgiver": "Stormote AS",
                            "sted": "Oslo",
                            "stillingstittel": "Butikkmedarbeider klesbutikk",
                            "beskrivelse": "Jobb i butikk som drev med både klær og tekstil. Som grossist og til privatkunder."
                          },
                          {
                            "fraDato": "2015-05-01T00:00:00+02:00",
                            "tilDato": "2018-03-01T00:00:00+01:00",
                            "arbeidsgiver": "H&M Storo",
                            "sted": "Oslo",
                            "stillingstittel": "Butikkmedarbeider klesbutikk",
                            "beskrivelse": "Ordinær ansatt i klesbutikk. Hadde behov for å trappe ned etter en stressende stilling som daglig leder hos Carlings. Generelt butikkarbeid, salg."
                          }
                        ],
                        "ønsketYrke": [
                          "Kokkelærling",
                          "Skipskokk"
                        ],
                        "sammendrag": "Dette er et sammendrag.",
                        "utdanning": [
                          {
                            "utdanningsretning": "Master markedsføring",
                            "beskrivelse": "Markedsføring",
                            "utdannelsessted": "NHH",
                            "fra": "2019-08-01T00:00:00+02:00",
                            "til": "2021-06-01T00:00:00+02:00"
                          },
                          {
                            "utdanningsretning": "Bachelor i Sykepleie",
                            "beskrivelse": "Sykepleie",
                            "utdannelsessted": "Lovisenberg høyskole",
                            "fra": "1997-06-01T00:00:00+02:00",
                            "til": "2000-06-01T00:00:00+02:00"
                          }
                        ],
                        "språk": [
                          {
                            "navn": "Engelsk",
                            "muntlig": "VELDIG_GODT",
                            "skriftlig": "GODT"
                          },
                          {
                            "navn": "Norsk",
                            "muntlig": "FOERSTESPRAAK",
                            "skriftlig": "FOERSTESPRAAK"
                          }
                        ]
                      }
                    }
                  ]
                }
            """.trim())
            )
    }

    @Test
    fun `GET mot kandidaterliste gir status 200`() {
        val uuid = UUID.randomUUID()

        repository.lagre(
            kandidatliste().copy(
                virksomhetsnummer = "123456788",
                stillingId = uuid
            )
        )
        val (_, response) = Fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=123456788")
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(response.body().asString("application/json;charset=utf-8"))
            .contains(("""
                [
                  {
                    "kandidatliste": {
                      "uuid":"7ea380f8-a0af-433f-8cbc-51c5788a7d29",
                      "stillingId": "$uuid",
                      "tittel": "Tittel",
                      "status": "ÅPEN",
                      "slettet": false,
                      "virksomhetsnummer": "123456788",
                      "sistEndret":"
                     """.filter { !it.isWhitespace() }))

            .contains("""  
                      "
                    },
                    "antallKandidater": 0
                  }
                ]
            """.filter { !it.isWhitespace() })
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

    private fun kandidat(uuid: UUID = UUID.randomUUID(), kandidatlisteid: BigInteger) = Kandidat(
        uuid = uuid,
        aktørId = "123",
        kandidatlisteId = kandidatlisteid
    )

    fun stubHentingAvEnKandidat(aktørId: String, responsBody: String) {
        wiremockServer.stubFor(
            WireMock.get("/veilederkandidat_current/_search?q=aktorId:$aktørId")
            .withBasicAuth("gunnar", "xyz")
            .willReturn(WireMock.ok(responsBody)))
    }
}
