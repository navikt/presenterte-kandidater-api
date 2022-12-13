package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import io.javalin.Javalin
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.OpenSearchKlient
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.*
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetKandidatlisterTest {
    private val mockOAuth2Server = MockOAuth2Server()
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    private val fuel = FuelManager()
    private lateinit var javalin: Javalin
    private val wiremockServer = hentWiremock()

    @BeforeAll
    fun init() {
        mockOAuth2Server.start(port = 18301)
        startLocalApplication()
    }

    @AfterAll
    fun cleanUp() {
        mockOAuth2Server.shutdown()
    }

    @AfterEach
    fun afterEach() {
        wiremockServer.resetAll()
    }

    @Test
    fun `Svarer 401 Unauthorized hvis forespørselen ikke inneholder et token`() {
        val endepunkt = "http://localhost:9000/kandidatlister"
        val (_, response) = fuel
            .get(endepunkt)
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `Svarer 401 Unauthorized hvis forespørselens token er ugyldig`() {
        val endepunkt = "http://localhost:9000/kandidatlister"
        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentUgyldigToken(mockOAuth2Server))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `Svarer 400 Bad Request hvis URL-en ikke inneholder virksomhetsnummer`() {
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "123456789"),
            Testdata.lagAltinnOrganisasjon("Et Navn", "987654321"),
        )
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)
        val endepunkt = "http://localhost:9000/kandidatlister"
        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(400)
    }

    @Test
    fun `Returnerer 200 OK med alle kandidatlister tilknyttet oppgitt virksomhetsnummer`() {
        val stillingId = UUID.randomUUID()
        val virksomhetsnummer = "323534343"
        val endepunkt = "http://localhost:9000/kandidatlister?virksomhetsnummer=$virksomhetsnummer"
        val kandidatliste = kandidatliste().copy(
            virksomhetsnummer = virksomhetsnummer,
            stillingId = stillingId
        )
        repository.lagre(kandidatliste)
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummer),
        )
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(200)

        val kandidatlisteMedKandidaterJson =
            defaultObjectMapper.readTree(response.body().asString("application/json;charset=utf-8"))
        val kandidatlisteJson = kandidatlisteMedKandidaterJson[0]["kandidatliste"]
        val antallKandidater = kandidatlisteMedKandidaterJson[0]["antallKandidater"]
        Assertions.assertThat(antallKandidater.asInt()).isZero()
        Assertions.assertThat(UUID.fromString(kandidatlisteJson["uuid"].textValue())).isEqualTo(kandidatliste.uuid)
        Assertions.assertThat(UUID.fromString(kandidatlisteJson["stillingId"].textValue())).isEqualTo(stillingId)
        Assertions.assertThat(kandidatlisteJson["virksomhetsnummer"].textValue()).isEqualTo(virksomhetsnummer)
        Assertions.assertThat(kandidatlisteJson["slettet"].asBoolean()).isFalse
        Assertions.assertThat(kandidatlisteJson["status"].textValue()).isEqualTo(Kandidatliste.Status.ÅPEN.toString())
        Assertions.assertThat(kandidatlisteJson["tittel"].textValue()).isEqualTo("Tittel")
        assertNull(kandidatlisteJson["id"])
        Assertions.assertThat(ZonedDateTime.parse(kandidatlisteJson["sistEndret"].textValue())).isCloseTo(
            kandidatliste.sistEndret,
            Assertions.within(3, ChronoUnit.SECONDS)
        )
        Assertions.assertThat(ZonedDateTime.parse(kandidatlisteJson["opprettet"].textValue())).isCloseTo(
            kandidatliste.opprettet,
            Assertions.within(3, ChronoUnit.SECONDS)
        )
    }

    @Test
    fun `Returnerer kandidatlistene sortert på opprettet-dato`() {
        val virksomhetsnummer = "123456788"
        val endepunkt = "http://localhost:9000/kandidatlister?virksomhetsnummer=$virksomhetsnummer"
        val kandidatlisteOpprettet1UkeSiden = kandidatliste().copy(opprettet = ZonedDateTime.now().minusWeeks(1), virksomhetsnummer = virksomhetsnummer)
        val kandidatlisteOpprettet1MånedSiden = kandidatliste().copy(opprettet = ZonedDateTime.now().minusMonths(1), virksomhetsnummer = virksomhetsnummer)
        val kandidatlisteOpprettet1ÅrSiden = kandidatliste().copy(opprettet = ZonedDateTime.now().minusYears(1), virksomhetsnummer = virksomhetsnummer)
        repository.lagre(kandidatlisteOpprettet1UkeSiden)
        repository.lagre(kandidatlisteOpprettet1ÅrSiden)
        repository.lagre(kandidatlisteOpprettet1MånedSiden)
        val organisasjoner = listOf(Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummer),)
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        val kandidatlisterMedKandidaterJson = defaultObjectMapper.readTree(response.body().asString("application/json;charset=utf-8"))
        val opprettetDatoFørsteListe = ZonedDateTime.parse(kandidatlisterMedKandidaterJson[0]["kandidatliste"]["opprettet"].textValue())
        val opprettetDatoAndreListe = ZonedDateTime.parse(kandidatlisterMedKandidaterJson[1]["kandidatliste"]["opprettet"].textValue())
        val opprettetDatoTredjeListe = ZonedDateTime.parse(kandidatlisterMedKandidaterJson[2]["kandidatliste"]["opprettet"].textValue())
        Assertions.assertThat(opprettetDatoFørsteListe).isAfter(opprettetDatoAndreListe)
        Assertions.assertThat(opprettetDatoAndreListe).isAfter(opprettetDatoTredjeListe)
    }

    @Test
    fun `Returnerer 403 når man forsøker å hente kandidatlister på organisasjoner der man ikke har noen rettigheter`() {
        val virksomhetsnummerManForsøkerÅHenteKandidatlisterFor = "123456789"
        val virksomhetsnummerManHarRettighetTil = "987654321"
        val stillingId = UUID.randomUUID()
        val endepunkt = "http://localhost:9000/kandidatlister?virksomhetsnummer=$virksomhetsnummerManForsøkerÅHenteKandidatlisterFor"
        val kandidatliste = kandidatliste().copy(
            virksomhetsnummer = virksomhetsnummerManForsøkerÅHenteKandidatlisterFor,
            stillingId = stillingId
        )
        repository.lagre(kandidatliste)
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummerManHarRettighetTil),
        )
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(403)
        val jsonbody = response.body().asString("application/json;charset=utf-8")
        Assertions.assertThat(jsonbody.isEmpty())
    }

    @Test
    fun `Skal bruke Altinn-cache`() {
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "123456789"),
            Testdata.lagAltinnOrganisasjon("Et Navn", "987654321"),
        )
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)
        val accessToken = hentToken(mockOAuth2Server, tilfeldigFødselsnummer())

        val (_, respons1, _) = fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=987654321")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()
        Assertions.assertThat(respons1.statusCode).isEqualTo(200)

        val (_, respons2, _) = fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=987654321")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()
        Assertions.assertThat(respons2.statusCode).isEqualTo(200)

        wiremockServer.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo(tokenXWiremockUrl)))
        wiremockServer.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo(altinnProxyUrlFiltrertPåRekruttering)))
    }

    @Test
    fun `Bruker ikke cache når Altinn returnerer tom liste av organisasjoner`() {
        val tomListeAvOrganisasjoner = listOf<AltinnReportee>()
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, tomListeAvOrganisasjoner)
        val accessToken = hentToken(mockOAuth2Server, tilfeldigFødselsnummer())

        fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=987654321")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()

        fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=987654321")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()

        wiremockServer.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo(tokenXWiremockUrl)))
        wiremockServer.verify(2, WireMock.getRequestedFor(WireMock.urlEqualTo(altinnProxyUrlFiltrertPåRekruttering)))
    }

    @Test
    fun `Bruker ikke cache når det hentes organisasjoner for annen bruker`() {
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "123456789"),
            Testdata.lagAltinnOrganisasjon("Et Navn", "987654321"),
        )
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)
        val accessToken = hentToken(mockOAuth2Server, tilfeldigFødselsnummer())
        val accessToken2 = hentToken(mockOAuth2Server, tilfeldigFødselsnummer())

        val (_, respons1, _) = fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=987654321")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()
        Assertions.assertThat(respons1.statusCode).isEqualTo(200)

        val (_, respons2, _) = fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=987654321")
            .authentication().bearer(accessToken2)
            .responseObject<List<AltinnReportee>>()
        Assertions.assertThat(respons2.statusCode).isEqualTo(200)

        wiremockServer.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo(tokenXWiremockUrl)))
        wiremockServer.verify(2, WireMock.getRequestedFor(WireMock.urlEqualTo(altinnProxyUrlFiltrertPåRekruttering)))
    }

    @Test
    fun `Bruker ikke cache når det har gått mer enn 15 minutter fra forrige kall`() {
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", "123456789"),
            Testdata.lagAltinnOrganisasjon("Et Navn", "987654321"),
        )
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)
        val accessToken = hentToken(mockOAuth2Server, tilfeldigFødselsnummer())
        val accessToken2 = hentToken(mockOAuth2Server, tilfeldigFødselsnummer())

        val (_, respons1, _) = fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=987654321")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()
        Assertions.assertThat(respons1.statusCode).isEqualTo(200)

        // Setter klokka fram i tid
        val constantClock: Clock =
            Clock.fixed(ZonedDateTime.now().plusMinutes(15).plusNanos(1).toInstant(), ZoneId.systemDefault())

        val (_, respons2, _) = fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=987654321")
            .authentication().bearer(accessToken2)
            .responseObject<List<AltinnReportee>>()
        Assertions.assertThat(respons2.statusCode).isEqualTo(200)

        wiremockServer.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo(tokenXWiremockUrl)))
        wiremockServer.verify(2, WireMock.getRequestedFor(WireMock.urlEqualTo(altinnProxyUrlFiltrertPåRekruttering)))

        // Setter klokka tilbake
        Clock.offset(constantClock, Duration.ZERO)
    }

    @Test
    fun `Returnerer ikke lister som er slettet`() {
        val stillingId = UUID.randomUUID()
        val endepunkt = "http://localhost:9000/kandidatlister?virksomhetsnummer=123456788"
        val virksomhetsnummer = "123456788"
        val kandidatliste = kandidatliste().copy(
            virksomhetsnummer = virksomhetsnummer,
            stillingId = stillingId,
            slettet = true

        )
        repository.lagre(kandidatliste)
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummer),
        )
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(mockOAuth2Server, tilfeldigFødselsnummer()))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(200)

        val kandidatlisteMedKandidaterJson =
            defaultObjectMapper.readTree(response.body().asString("application/json;charset=utf-8"))
        Assertions.assertThat(kandidatlisteMedKandidaterJson).isEmpty()
    }
}
