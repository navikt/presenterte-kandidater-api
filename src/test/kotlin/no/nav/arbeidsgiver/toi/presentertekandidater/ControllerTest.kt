package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.databind.JsonNode
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.toi.presentertekandidater.Kandidat.ArbeidsgiversVurdering.TIL_VURDERING
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.http.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.random.Random
import kotlin.test.assertNotNull
import kotlin.test.assertNull


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ControllerTest {
    private val mockOAuth2Server = MockOAuth2Server()
    private val javalin = opprettJavalinMedTilgangskontrollForTest(issuerProperties)
    private val repository = opprettTestRepositoryMedLokalPostgres()
    private val wiremockServer = WireMockServer(wiremockPort)
    private val fuel = FuelManager()
    private val altinnProxyWiremockUrl =
        "/altinn-proxy-url/v2/organisasjoner?top=500&skip=0&serviceCode=5078&serviceEdition=1&filter=Type+ne+%27Person%27+and+Status+eq+%27Active%27"
    private val tokenXWiremockUrl = "/token-x-token-endpoint"

    lateinit var openSearchKlient: OpenSearchKlient

    @BeforeAll
    fun init() {
        mockOAuth2Server.start(port = 18301)
        wiremockServer.start()
        openSearchKlient = OpenSearchKlient(
            mapOf(
                "OPEN_SEARCH_URI" to "http://localhost:${wiremockServer.port()}",
                "OPEN_SEARCH_USERNAME" to "gunnar",
                "OPEN_SEARCH_PASSWORD" to "xyz"
            )
        )

        startLocalApplication(javalin = javalin, repository = repository, openSearchKlient = openSearchKlient)
    }

    @AfterEach
    fun afterEach() {
        wiremockServer.resetAll()
    }

    @AfterAll
    fun cleanUp() {
        mockOAuth2Server.shutdown()
        javalin.stop()
        wiremockServer.shutdown()
    }

    @Test
    fun `GET mot kandidatlister-endepunkt svarer 403 Forbidden hvis forespørselen ikke inneholder et token`() {
        val endepunkt = "http://localhost:9000/kandidatlister"
        val (_, response) = fuel
            .get(endepunkt)
            .response()

        assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `GET mot kandidatlister-endepunkt svarer 403 Forbidden hvis forespørselens token er ugyldig`() {
        val endepunkt = "http://localhost:9000/kandidatlister"
        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentUgyldigToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `GET mot kandidatlister-endepunkt uten virksomhetsnummer svarer 400 Bad Request`() {
        val exchangeToken = "exchangeToken"
        stubVekslingAvTokenX(exchangeToken)
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "123456789"),
            Testdata.lagAltinnOrganisasjon("Et Navn", "987654321"),
        )
        stubHentingAvOrganisasjoner(exchangeToken, organisasjoner)
        val endepunkt = "http://localhost:9000/kandidatlister"
        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(400)
    }

    @Test
    fun `GET mot kandidatlister-endepunkt returnerer 200 OK med alle kandidatlister tilknyttet oppgitt virksomhetsnummer`() {
        val stillingId = UUID.randomUUID()
        val endepunkt = "http://localhost:9000/kandidatlister?virksomhetsnummer=123456788"
        val virksomhetsnummer = "123456788"
        val kandidatliste = kandidatliste().copy(
            virksomhetsnummer = virksomhetsnummer,
            stillingId = stillingId
        )
        repository.lagre(kandidatliste)
        val exchangeToken = "exchangeToken"
        stubVekslingAvTokenX(exchangeToken)
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummer),
        )
        stubHentingAvOrganisasjoner(exchangeToken, organisasjoner)

        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(200)

        val kandidatlisteMedKandidaterJson =
            defaultObjectMapper.readTree(response.body().asString("application/json;charset=utf-8"))
        val kandidatlisteJson = kandidatlisteMedKandidaterJson[0]["kandidatliste"]
        val antallKandidater = kandidatlisteMedKandidaterJson[0]["antallKandidater"]
        assertThat(antallKandidater.asInt()).isZero()
        assertThat(UUID.fromString(kandidatlisteJson["uuid"].textValue())).isEqualTo(kandidatliste.uuid)
        assertThat(UUID.fromString(kandidatlisteJson["stillingId"].textValue())).isEqualTo(stillingId)
        assertThat(kandidatlisteJson["virksomhetsnummer"].textValue()).isEqualTo(virksomhetsnummer)
        assertThat(kandidatlisteJson["slettet"].asBoolean()).isFalse
        assertThat(kandidatlisteJson["status"].textValue()).isEqualTo(Kandidatliste.Status.ÅPEN.toString())
        assertThat(kandidatlisteJson["tittel"].textValue()).isEqualTo("Tittel")
        assertNull(kandidatlisteJson["id"])
        assertThat(ZonedDateTime.parse(kandidatlisteJson["sistEndret"].textValue())).isCloseTo(
            kandidatliste.sistEndret,
            within(3, ChronoUnit.SECONDS)
        )
        assertThat(ZonedDateTime.parse(kandidatlisteJson["opprettet"].textValue())).isCloseTo(
            kandidatliste.opprettet,
            within(3, ChronoUnit.SECONDS)
        )
    }

    @Test
    fun `GET mot kandidatlister-endepunkt returnerer 403 når man forsøker å hente kandidatlister på organisasjoner der man ikke har noen rettigheter`() {
        val virksomhetsnummerManForsøkerÅHenteKandidatlisterFor = "123456789"
        val virksomhetsnummerManHarRettighetTil = "987654321"
        val stillingId = UUID.randomUUID()
        val endepunkt = "http://localhost:9000/kandidatlister?virksomhetsnummer=$virksomhetsnummerManForsøkerÅHenteKandidatlisterFor"
        val kandidatliste = kandidatliste().copy(
            virksomhetsnummer = virksomhetsnummerManForsøkerÅHenteKandidatlisterFor,
            stillingId = stillingId
        )
        repository.lagre(kandidatliste)
        val exchangeToken = "exchangeToken"
        stubVekslingAvTokenX(exchangeToken)
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummerManHarRettighetTil),
        )
        stubHentingAvOrganisasjoner(exchangeToken, organisasjoner)

        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(403)
        val jsonbody = response.body().asString("application/json;charset=utf-8")
        assertThat(jsonbody.isEmpty())
    }

    @Test
    fun `GET mot kandidatliste-endepunkt returnerer en kandidatliste og kandidater med CV`() {
        val stillingId = UUID.randomUUID()
        val endepunkt = "http://localhost:9000/kandidatliste/$stillingId"
        val nå = ZonedDateTime.now()
        val exchangeToken = "exchangeToken"
        stubVekslingAvTokenX(exchangeToken)

        val virksomhetsnummer = "111111111"
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummer),
        )
        stubHentingAvOrganisasjoner(exchangeToken, organisasjoner)


        repository.lagre(kandidatliste().copy(stillingId = stillingId, virksomhetsnummer = virksomhetsnummer))

        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat1 = Kandidat(
            aktørId = "1234",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = TIL_VURDERING,
            sistEndret = nå
        )
        val kandidat2 = Kandidat(
            aktørId = "666",
            kandidatlisteId = kandidatliste.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = TIL_VURDERING,
            sistEndret = nå
        )

        repository.lagre(kandidat1)
        repository.lagre(kandidat2)

        val esRespons = Testdata.flereKandidaterFraES(aktørId1 = kandidat1.aktørId, aktørid2 = kandidat2.aktørId)
        stubHentingAvKandidater(
            requestBody = openSearchKlient.lagBodyForHentingAvCver(
                listOf(
                    kandidat1.aktørId,
                    kandidat2.aktørId
                )
            ), responsBody = esRespons
        )

        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        assertThat(response.statusCode).isEqualTo(200)

        val jsonbody = response.body().asString("application/json;charset=utf-8")
        val kandidatlisteMedKandidaterJson = defaultObjectMapper.readTree(jsonbody)
        val kandidatlisteJson = kandidatlisteMedKandidaterJson["kandidatliste"]
        val kandidaterJson = kandidatlisteMedKandidaterJson["kandidater"]

        assertNull(kandidatlisteJson["id"])
        assertThat(kandidatlisteJson["status"].textValue()).isEqualTo(Kandidatliste.Status.ÅPEN.toString())
        assertThat(kandidatlisteJson["tittel"].textValue()).isEqualTo(kandidatliste.tittel)
        assertThat(ZonedDateTime.parse(kandidatlisteJson["sistEndret"].textValue())).isEqualTo(kandidatliste.sistEndret)
        assertThat(ZonedDateTime.parse(kandidatlisteJson["opprettet"].textValue())).isEqualTo(kandidatliste.opprettet)
        assertThat(kandidatlisteJson["slettet"].asBoolean()).isFalse
        assertThat(UUID.fromString(kandidatlisteJson["stillingId"].textValue())).isEqualTo(stillingId)
        assertThat(UUID.fromString(kandidatlisteJson["uuid"].textValue())).isEqualTo(kandidatliste.uuid)
        assertThat(kandidatlisteJson["virksomhetsnummer"].textValue()).isEqualTo(kandidatliste.virksomhetsnummer)

        assertThat(kandidaterJson).hasSize(2)
        assertKandidat(kandidaterJson[0], kandidat1)
        assertKandidat(kandidaterJson[1], kandidat2)
        assertNotNull(kandidaterJson[0]["cv"]);
        assertNotNull(kandidaterJson[1]["cv"]);
    }

    @Test
    fun `GET mot kandidatliste-endepunkt returnerer 403 når man ikke representerer virksomheten kandidatlista tilhører`() {
        val virksomhetsnummerManRepresenterer = "987654321"
        val virksomhetsnummerTilkandidatlista = "123456789"
        val stillingId = UUID.fromString("4bd2c240-92d2-4166-ac54-ba3d21bfbc07")
        val endepunkt = "http://localhost:9000/kandidatliste/$stillingId"
        val nå = ZonedDateTime.now()
        val exchangeToken = "exchangeToken"
        stubVekslingAvTokenX(exchangeToken)
        stubHentingAvOrganisasjoner(exchangeToken, listOf(Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummerManRepresenterer)))
        repository.lagre(kandidatliste().copy(stillingId = stillingId, virksomhetsnummer = virksomhetsnummerTilkandidatlista))

        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat1 = Kandidat(
            aktørId = "1234",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = TIL_VURDERING,
            sistEndret = nå
        )
        val kandidat2 = Kandidat(
            aktørId = "666",
            kandidatlisteId = kandidatliste.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = TIL_VURDERING,
            sistEndret = nå
        )
        repository.lagre(kandidat1)
        repository.lagre(kandidat2)


        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(403)

        val jsonbody = response.body().asString("application/json;charset=utf-8")
        assertThat(jsonbody.isEmpty())
    }

    @Test
    fun `PUT mot vurdering-endepunkt oppdaterer arbeidsgivers vurdering og returnerer 200 OK`() {
        val stillingId = UUID.randomUUID()
        val virksomhetsnummer = "123456789"
        repository.lagre(kandidatliste().copy(stillingId = stillingId, virksomhetsnummer = virksomhetsnummer))
        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat = Kandidat(
            aktørId = "1234",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = TIL_VURDERING,
            sistEndret = ZonedDateTime.now().minusDays(1)
        )
        repository.lagre(kandidat)
        val exchangeToken = "exchangeToken"
        stubVekslingAvTokenX(exchangeToken)
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummer),
            Testdata.lagAltinnOrganisasjon("Et Navn", "987654321"),
        )
        stubHentingAvOrganisasjoner(exchangeToken, organisasjoner)

        val body = """
            {
              "arbeidsgiversVurdering": "FÅTT_JOBBEN"
            }
        """.trimIndent()

        val (_, response) = fuel
            .put("http://localhost:9000/kandidat/${kandidat.uuid}/vurdering")
            .jsonBody(body)
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        assertThat(response.statusCode).isEqualTo(200)
        val kandidatFraDatabasen = repository.hentKandidat(kandidat.aktørId, kandidatliste.id!!)
        assertThat(kandidatFraDatabasen!!.arbeidsgiversVurdering).isEqualTo(Kandidat.ArbeidsgiversVurdering.FÅTT_JOBBEN)
        assertThat(kandidatFraDatabasen.sistEndret).isEqualToIgnoringSeconds(ZonedDateTime.now())
    }

    @Test
    fun `PUT mot vurdering-endepunkt med nullverdi skal returnere 400`() {
        val stillingId = UUID.randomUUID()
        repository.lagre(kandidatliste().copy(stillingId = stillingId))
        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat = Kandidat(
            aktørId = "1234",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = TIL_VURDERING,
            sistEndret = ZonedDateTime.now().minusDays(1)
        )
        repository.lagre(kandidat)
        val exchangeToken = "exchangeToken"
        stubVekslingAvTokenX(exchangeToken)
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "123456789"),
            Testdata.lagAltinnOrganisasjon("Et Navn", "987654321"),
        )
        stubHentingAvOrganisasjoner(exchangeToken, organisasjoner)

        val body = """
            {
              "arbeidsgiversVurdering": null
            }
        """.trimIndent()

        val (_, response) = fuel
            .put("http://localhost:9000/kandidat/${kandidat.uuid}/vurdering")
            .jsonBody(body)
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        assertThat(response.statusCode).isEqualTo(400)
        val kandidatFraDatabasen = repository.hentKandidat(kandidat.aktørId, kandidatliste.id!!)
        assertThat(kandidatFraDatabasen!!.arbeidsgiversVurdering).isEqualTo(kandidat.arbeidsgiversVurdering)
        assertThat(kandidatFraDatabasen.sistEndret).isEqualToIgnoringNanos(kandidat.sistEndret)
    }

    @Test
    @Disabled("Disablet fordi denne feiler med statuskode -1 av ukjent grunn på GHA, ikke lokalt.")
    fun `PUT mot vurdering-endepunkt med ukjent verdi skal returnere 400`() {
        val body = """
            {
              "arbeidsgiversVurdering": "NY"
            }
        """.trimIndent()

        val (_, response) = fuel
            .put("http://localhost:9000/kandidat/${UUID.randomUUID()}/vurdering")
            .jsonBody(body)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(400)
    }

    @Test
    fun `PUT mot vurdering-endepunkt gir 400 hvis kandidat ikke eksisterer`() {
        val exchangeToken = "exchangeToken"
        stubVekslingAvTokenX(exchangeToken)
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "123456789"),
            Testdata.lagAltinnOrganisasjon("Et Navn", "987654321"),
        )
        stubHentingAvOrganisasjoner(exchangeToken, organisasjoner)
        val body = """
            {
              "arbeidsgiversVurdering": "FÅTT_JOBBEN"
            }
        """.trimIndent()

        val (_, response) = fuel
            .put("http://localhost:9000/kandidat/${UUID.randomUUID()}/vurdering")
            .jsonBody(body)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(400)
    }

    @Test
    fun `PUT mot vurdering-endepunkt gir 403 hvis bruker ikke representerer virksomheten`() {
        val kandidatlistasVirksomhetsnummer = "123456789"
        val innloggetBrukersVirksomhetsnummer = "987654321"
        val stillingId = UUID.randomUUID()
        repository.lagre(kandidatliste().copy(stillingId = stillingId, virksomhetsnummer = kandidatlistasVirksomhetsnummer))
        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat = Kandidat(
            aktørId = "1234",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = TIL_VURDERING,
            sistEndret = ZonedDateTime.now().minusDays(1)
        )
        repository.lagre(kandidat)
        val exchangeToken = "exchangeToken"
        stubVekslingAvTokenX(exchangeToken)
        val organisasjoner = listOf(Testdata.lagAltinnOrganisasjon("Et Navn", innloggetBrukersVirksomhetsnummer),)
        stubHentingAvOrganisasjoner(exchangeToken, organisasjoner)
        val body = """
            {
              "arbeidsgiversVurdering": "FÅTT_JOBBEN"
            }
        """.trimIndent()

        val (_, response) = fuel
            .put("http://localhost:9000/kandidat/${kandidat.uuid}/vurdering")
            .jsonBody(body)
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `GET mot organisasjoner-endepunkt gir 200 og liste over organisasjoner bruker representerer`() {
        val fødselsnummerInnloggetBruker = "unikt764398"
        val accessToken = hentToken(mockOAuth2Server, fødselsnummerInnloggetBruker)
        val exchangeToken = "exchangeToken"
        stubVekslingAvTokenX(exchangeToken)
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "123456789"),
            Testdata.lagAltinnOrganisasjon("Et Navn", "987654321"),
        )
        stubHentingAvOrganisasjoner(exchangeToken, organisasjoner)

        val (_, respons, result) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()

        assertThat(respons.statusCode).isEqualTo(200)
        val organisasjonerFraRespons = result.get()
        assertThat(organisasjonerFraRespons).hasSize(2)
        assertThat(organisasjoner[0]).isEqualTo(organisasjoner[0])
        assertThat(organisasjoner[1]).isEqualTo(organisasjoner[1])
    }

    @Test
    fun `GET mot organisasjoner-endepunkt gir 200 og tom liste hvis bruker ikke har rolle i noen organisasjoner`() {
        val fødselsnummerInnloggetBruker = tilfeldigFødselsnummer()
        val accessToken = hentToken(mockOAuth2Server, fødselsnummerInnloggetBruker)
        val exchangeToken = "exchangeToken"
        stubVekslingAvTokenX(exchangeToken)
        val organisasjoner = emptyList<AltinnReportee>()
        stubHentingAvOrganisasjoner(exchangeToken, organisasjoner)

        val (_, respons, result) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()

        assertThat(respons.statusCode).isEqualTo(200)
        val organisasjonerFraRespons = result.get()
        assertThat(organisasjonerFraRespons).hasSize(0)
    }

    @Test
    fun `GET mot organisasjoner-endepunkt bruker cache`() {
        val exchangeToken = "exchangeToken"
        stubVekslingAvTokenX(exchangeToken)
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "123456789"),
            Testdata.lagAltinnOrganisasjon("Et Navn", "987654321"),
        )
        stubHentingAvOrganisasjoner(exchangeToken, organisasjoner)
        val fødselsnummerInnloggetBruker = tilfeldigFødselsnummer()
        val accessToken = hentToken(mockOAuth2Server, fødselsnummerInnloggetBruker)

        val (_, respons1, result1) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()

        assertThat(respons1.statusCode).isEqualTo(200)
        val organisasjonerFraRespons1 = result1.get()
        assertThat(organisasjonerFraRespons1).hasSize(organisasjoner.size)

        val (_, respons2, result2) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()

        assertThat(respons2.statusCode).isEqualTo(200)
        val organisasjonerFraRespons2 = result2.get()
        assertThat(organisasjonerFraRespons2).hasSize(organisasjoner.size)

        wiremockServer.verify(1, postRequestedFor(urlEqualTo(tokenXWiremockUrl)))
        wiremockServer.verify(1, getRequestedFor(urlEqualTo(altinnProxyWiremockUrl)))
    }

    @Test
    fun `GET mot organisasjoner-endepunkt bruker ikke cache når Altinn returnerer tom liste av organisasjoner`() {
        val exchangeToken = "exchangeToken"
        stubVekslingAvTokenX(exchangeToken)
        val tomListeAvOrganisasjoner = listOf<AltinnReportee>()
        stubHentingAvOrganisasjoner(exchangeToken, tomListeAvOrganisasjoner)
        val fødselsnummerInnloggetBruker = tilfeldigFødselsnummer()
        val accessToken = hentToken(mockOAuth2Server, fødselsnummerInnloggetBruker)

        val (_, respons1, result1) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()

        assertThat(respons1.statusCode).isEqualTo(200)
        val organisasjonerFraRespons1 = result1.get()
        assertThat(organisasjonerFraRespons1).hasSize(tomListeAvOrganisasjoner.size)

        val (_, respons2, result2) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()

        assertThat(respons2.statusCode).isEqualTo(200)
        val organisasjonerFraRespons2 = result2.get()
        assertThat(organisasjonerFraRespons2).hasSize(tomListeAvOrganisasjoner.size)

        wiremockServer.verify(2, postRequestedFor(urlEqualTo(tokenXWiremockUrl)))
        wiremockServer.verify(2, getRequestedFor(urlEqualTo(altinnProxyWiremockUrl)))
    }

    @Test
    fun `GET mot organisasjoner-endepunkt bruker ikke cache når det hentes organisasjoner for annen bruker`() {
        val exchangeToken = "exchangeToken"
        stubVekslingAvTokenX(exchangeToken)
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "123456789"),
            Testdata.lagAltinnOrganisasjon("Et Navn", "987654321"),
        )
        stubHentingAvOrganisasjoner(exchangeToken, organisasjoner)
        val fødselsnummerInnloggetBruker = tilfeldigFødselsnummer()
        val fødselsnummerInnloggetBruker2 = tilfeldigFødselsnummer()
        val accessToken = hentToken(mockOAuth2Server, fødselsnummerInnloggetBruker)
        val accessToken2 = hentToken(mockOAuth2Server, fødselsnummerInnloggetBruker2)

        val (_, respons1, result1) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()

        assertThat(respons1.statusCode).isEqualTo(200)
        val organisasjonerFraRespons1 = result1.get()
        assertThat(organisasjonerFraRespons1).hasSize(organisasjoner.size)

        val (_, respons2, result2) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(accessToken2)
            .responseObject<List<AltinnReportee>>()

        assertThat(respons2.statusCode).isEqualTo(200)
        val organisasjonerFraRespons2 = result2.get()
        assertThat(organisasjonerFraRespons2).hasSize(organisasjoner.size)

        wiremockServer.verify(2, postRequestedFor(urlEqualTo(tokenXWiremockUrl)))
        wiremockServer.verify(2, getRequestedFor(urlEqualTo(altinnProxyWiremockUrl)))
    }

    @Test
    fun `GET mot organisasjoner-endepunkt bruker ikke cache når det har gått mer enn 15 minutter fra forrige kall`() {
        val exchangeToken = "exchangeToken"
        stubVekslingAvTokenX(exchangeToken)
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "123456789"),
            Testdata.lagAltinnOrganisasjon("Et Navn", "987654321"),
        )
        stubHentingAvOrganisasjoner(exchangeToken, organisasjoner)
        val fødselsnummerInnloggetBruker = tilfeldigFødselsnummer()
        val fødselsnummerInnloggetBruker2 = tilfeldigFødselsnummer()
        val accessToken = hentToken(mockOAuth2Server, fødselsnummerInnloggetBruker)
        val accessToken2 = hentToken(mockOAuth2Server, fødselsnummerInnloggetBruker2)

        val (_, respons1, result1) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()

        assertThat(respons1.statusCode).isEqualTo(200)
        val organisasjonerFraRespons1 = result1.get()
        assertThat(organisasjonerFraRespons1).hasSize(organisasjoner.size)

        // Setter klokka fram i tid
        val constantClock: Clock =
            Clock.fixed(ZonedDateTime.now().plusMinutes(15).plusNanos(1).toInstant(), ZoneId.systemDefault())

        val (_, respons2, result2) = fuel
            .get("http://localhost:9000/organisasjoner")
            .authentication().bearer(accessToken2)
            .responseObject<List<AltinnReportee>>()

        assertThat(respons2.statusCode).isEqualTo(200)
        val organisasjonerFraRespons2 = result2.get()
        assertThat(organisasjonerFraRespons2).hasSize(organisasjoner.size)

        wiremockServer.verify(2, postRequestedFor(urlEqualTo(tokenXWiremockUrl)))
        wiremockServer.verify(2, getRequestedFor(urlEqualTo(altinnProxyWiremockUrl)))

        // Setter klokka tilbake
        Clock.offset(constantClock, Duration.ZERO)
    }

    @Test
    fun `GET mot kandidatlister-endepunkt returnerer ikke lister som er slettet`() {
        val stillingId = UUID.randomUUID()
        val endepunkt = "http://localhost:9000/kandidatlister?virksomhetsnummer=123456788"
        val virksomhetsnummer = "123456788"
        val kandidatliste = kandidatliste().copy(
            virksomhetsnummer = virksomhetsnummer,
            stillingId = stillingId,
            slettet = true

        )
        repository.lagre(kandidatliste)
        val exchangeToken = "exchangeToken"
        stubVekslingAvTokenX(exchangeToken)
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummer),
        )
        stubHentingAvOrganisasjoner(exchangeToken, organisasjoner)

        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        assertThat(response.statusCode).isEqualTo(200)

        val kandidatlisteMedKandidaterJson =
            defaultObjectMapper.readTree(response.body().asString("application/json;charset=utf-8"))
        assertThat(kandidatlisteMedKandidaterJson).isEmpty()
    }

     @Test
    fun `GET mot kandidatliste-endepunkt returnerer ikke en kandidatliste som er slettet`() {
        val stillingId = UUID.randomUUID()
        val endepunkt = "http://localhost:9000/kandidatliste/$stillingId"
         val exchangeToken = "exchangeToken"
         stubVekslingAvTokenX(exchangeToken)
         val virksomhetsnummer = "123456789"
         val organisasjoner = listOf(
             Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummer),
         )
         stubHentingAvOrganisasjoner(exchangeToken, organisasjoner)

        repository.lagre(kandidatliste().copy(stillingId = stillingId, slettet = true, virksomhetsnummer = virksomhetsnummer))

        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        assertThat(response.statusCode).isEqualTo(404)
    }

    private fun assertKandidat(fraRespons: JsonNode, fraDatabasen: Kandidat) {
        assertThat(fraRespons["kandidat"]).isNotEmpty
        assertNull(fraRespons["kandidat"]["id"])
        assertThat(UUID.fromString(fraRespons["kandidat"]["uuid"].textValue())).isEqualTo(fraDatabasen.uuid)
        assertThat(
            fraRespons["kandidat"]["arbeidsgiversVurdering"].textValue()
                .equals(fraDatabasen.arbeidsgiversVurdering.name)
        )
        assertThat(ZonedDateTime.parse(fraRespons["kandidat"]["sistEndret"].textValue()) == fraDatabasen.sistEndret)
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

    private fun stubHentingAvKandidater(requestBody: String, responsBody: String) {
        wiremockServer.stubFor(
            post("/veilederkandidat_current/_search")
                .withBasicAuth("gunnar", "xyz")
                .withRequestBody(containing(requestBody))
                .willReturn(ok(responsBody))
        )
    }

    private fun stubHentingAvOrganisasjoner(exchangeToken: String, organisasjoner: List<AltinnReportee>) {
        val organisasjonerJson = objectMapper.writeValueAsString(organisasjoner)
        wiremockServer.stubFor(
            get(altinnProxyWiremockUrl)
                .withHeader("Authorization", containing("Bearer $exchangeToken"))
                .willReturn(
                    ok(organisasjonerJson)
                        .withHeader("Content-Type", "application/json")
                )
        )
    }

    private fun stubVekslingAvTokenX(token: String) {
        val responseBody = """
            {
                "access_token": "$token",
                "expires_in": 123
            }
        """.trimIndent()

        wiremockServer.stubFor(
            post(tokenXWiremockUrl)
                .willReturn(ok(responseBody))
        )
    }

    private fun tilfeldigFødselsnummer(): String {
        fun Int.tilStrengMedToTegn() = this.toString().let {  if (it.length == 1) "0$it" else it }
        val tilfeldigDag = Random.nextInt(32).tilStrengMedToTegn()
        val tilfeldigMåned = Random.nextInt(13).tilStrengMedToTegn()
        val tilfeldigÅr = Random.nextInt(1910, 2010).tilStrengMedToTegn()
        val tilfeldigPersonnummer = Random.nextInt(10000, 90000)
        return "$tilfeldigDag$tilfeldigMåned$tilfeldigÅr$tilfeldigPersonnummer"
    }
}
