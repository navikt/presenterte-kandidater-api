package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnTilgang
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidatliste
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetKandidatlisterTest {
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    private val httpClient = HttpClient.newHttpClient()
    private val wiremockServer = hentWiremock()

    @BeforeAll
    fun init() {
        startLocalApplication()
    }

    @AfterEach
    fun afterEach() {
        wiremockServer.resetAll()
    }

    @Test
    fun `Svarer 401 Unauthorized hvis forespørselen ikke inneholder et token`() {
        val endepunkt = "http://localhost:9000/kandidatlister"
        val request = HttpRequest.newBuilder(URI(endepunkt))
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(401)
    }

    @Test
    fun `Svarer 401 Unauthorized hvis forespørselens token er ugyldig`() {
        val endepunkt = "http://localhost:9000/kandidatlister"
        val request = HttpRequest.newBuilder(URI(endepunkt))
            .header("Authorization", "Bearer ${hentUgyldigToken()}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(401)
    }

    @Test
    fun `Svarer 400 Bad Request hvis URL-en ikke inneholder virksomhetsnummer`() {
        val organisasjoner = listOf(
            Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", "123456789"),
            Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", "987654321"),
        )
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)
        val endepunkt = "http://localhost:9000/kandidatlister"
        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val request = HttpRequest.newBuilder(URI(endepunkt))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `Returnerer 451 hvis ikke samtykket til vilkår`() {
        val stillingId = UUID.randomUUID()
        val virksomhetsnummer = "98435243"
        val endepunkt = "http://localhost:9000/kandidatlister?virksomhetsnummer=$virksomhetsnummer"
        val kandidatliste = kandidatliste().copy(
            virksomhetsnummer = virksomhetsnummer,
            stillingId = stillingId
        )
        repository.lagre(kandidatliste)
        val organisasjoner = listOf(
            Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummer),
        )
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()

        val request = HttpRequest.newBuilder(URI(endepunkt))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(451)
    }

    @Test
    fun `Returnerer 200 OK med alle kandidatlister tilknyttet oppgitt virksomhetsnummer`() {
        val stillingId = UUID.randomUUID()
        val virksomhetsnummer = "323534343"
        val endepunkt = "http://localhost:9000/kandidatlister?virksomhetsnummer=$virksomhetsnummer"
        val vintertid = ZonedDateTime.parse("2024-01-15T12:30:00+01:00[Europe/Oslo]")
        val sommertid = ZonedDateTime.parse("2024-07-15T12:30:00+02:00[Europe/Oslo]")
        val kandidatliste = kandidatliste().copy(
            virksomhetsnummer = virksomhetsnummer,
            stillingId = stillingId,
            sistEndret = vintertid,
            opprettet = sommertid
        )
        repository.lagre(kandidatliste)
        val organisasjoner = listOf(
            Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummer),
        )
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val request = HttpRequest.newBuilder(URI(endepunkt))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)

        val kandidatlisteMedKandidaterJson =
            defaultObjectMapper.readTree(response.body())
        assertThat(kandidatlisteMedKandidaterJson[0].propertyNames().toSet())
            .containsExactlyInAnyOrder("kandidatliste", "antallKandidater")
        val kandidatlisteJson = kandidatlisteMedKandidaterJson[0]["kandidatliste"]
        val antallKandidater = kandidatlisteMedKandidaterJson[0]["antallKandidater"]
        assertThat(kandidatlisteJson.propertyNames().toSet())
            .containsExactlyInAnyOrder(
                "uuid", "stillingId", "tittel", "status", "slettet",
                "virksomhetsnummer", "sistEndret", "opprettet"
            )
        assertThat(kandidatlisteJson["sistEndret"].asString()).isEqualTo("2024-01-15T12:30:00+01:00")
        assertThat(kandidatlisteJson["opprettet"].asString()).isEqualTo("2024-07-15T12:30:00+02:00")
        assertThat(antallKandidater.asInt()).isZero()
        assertThat(UUID.fromString(kandidatlisteJson["uuid"].asString())).isEqualTo(kandidatliste.uuid)
        assertThat(UUID.fromString(kandidatlisteJson["stillingId"].asString())).isEqualTo(stillingId)
        assertThat(kandidatlisteJson["virksomhetsnummer"].asString()).isEqualTo(virksomhetsnummer)
        assertThat(kandidatlisteJson["slettet"].asBoolean()).isFalse
        assertThat(kandidatlisteJson["status"].asString()).isEqualTo(Kandidatliste.Status.ÅPEN.toString())
        assertThat(kandidatlisteJson["tittel"].asString()).isEqualTo("Tittel")
        assertNull(kandidatlisteJson["id"])
        assertThat(ZonedDateTime.parse(kandidatlisteJson["sistEndret"].asString())).isCloseTo(
            kandidatliste.sistEndret,
            Assertions.within(3, ChronoUnit.SECONDS)
        )
        assertThat(ZonedDateTime.parse(kandidatlisteJson["opprettet"].asString())).isCloseTo(
            kandidatliste.opprettet,
            Assertions.within(3, ChronoUnit.SECONDS)
        )
    }

    @Test
    fun `Returnerer kandidatlistene sortert på opprettet-dato`() {
        val virksomhetsnummer = "123456788"
        val endepunkt = "http://localhost:9000/kandidatlister?virksomhetsnummer=$virksomhetsnummer"
        val kandidatlisteOpprettet1UkeSiden =
            kandidatliste().copy(opprettet = ZonedDateTime.now().minusWeeks(1), virksomhetsnummer = virksomhetsnummer)
        val kandidatlisteOpprettet1MånedSiden =
            kandidatliste().copy(opprettet = ZonedDateTime.now().minusMonths(1), virksomhetsnummer = virksomhetsnummer)
        val kandidatlisteOpprettet1ÅrSiden =
            kandidatliste().copy(opprettet = ZonedDateTime.now().minusYears(1), virksomhetsnummer = virksomhetsnummer)
        repository.lagre(kandidatlisteOpprettet1UkeSiden)
        repository.lagre(kandidatlisteOpprettet1ÅrSiden)
        repository.lagre(kandidatlisteOpprettet1MånedSiden)
        val organisasjoner = listOf(Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummer))
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val request = HttpRequest.newBuilder(URI(endepunkt))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)
        val kandidatlisterMedKandidaterJson =
            defaultObjectMapper.readTree(response.body())
        val opprettetDatoFørsteListe =
            ZonedDateTime.parse(kandidatlisterMedKandidaterJson[0]["kandidatliste"]["opprettet"].asString())
        val opprettetDatoAndreListe =
            ZonedDateTime.parse(kandidatlisterMedKandidaterJson[1]["kandidatliste"]["opprettet"].asString())
        val opprettetDatoTredjeListe =
            ZonedDateTime.parse(kandidatlisterMedKandidaterJson[2]["kandidatliste"]["opprettet"].asString())
        assertThat(opprettetDatoFørsteListe).isAfter(opprettetDatoAndreListe)
        assertThat(opprettetDatoAndreListe).isAfter(opprettetDatoTredjeListe)
    }

    @Test
    fun `Returnerer 403 når man forsøker å hente kandidatlister på organisasjoner der man ikke har noen rettigheter`() {
        val virksomhetsnummerManForsøkerÅHenteKandidatlisterFor = "123456789"
        val virksomhetsnummerManHarRettighetTil = "987654321"
        val stillingId = UUID.randomUUID()
        val endepunkt =
            "http://localhost:9000/kandidatlister?virksomhetsnummer=$virksomhetsnummerManForsøkerÅHenteKandidatlisterFor"
        val kandidatliste = kandidatliste().copy(
            virksomhetsnummer = virksomhetsnummerManForsøkerÅHenteKandidatlisterFor,
            stillingId = stillingId
        )
        repository.lagre(kandidatliste)
        val organisasjoner = listOf(
            Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummerManHarRettighetTil),
        )
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val request = HttpRequest.newBuilder(URI(endepunkt))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(403)
        val jsonbody = response.body()
        assertThat(jsonbody.isEmpty())
    }

    @Test
    fun `Returnerer 503 når Altinn feiler`() {
        stubHttpStatus500FraAltinnProxy(wiremockServer)
        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidatlister?virksomhetsnummer=987654321"))
            .header("Authorization", "Bearer ${hentToken(tilfeldigFødselsnummer())}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(503)
    }

    @Test
    fun `Skal bruke Altinn-cache`() {
        val organisasjoner = listOf(
            Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", "123456789"),
            Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", "987654321"),
        )
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)
        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val accessToken = hentToken(fødselsnummer)

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidatlister?virksomhetsnummer=987654321"))
            .header("Authorization", "Bearer $accessToken")
            .build()
        val respons1 = httpClient.send(request, BodyHandlers.ofString())
        assertThat(respons1.statusCode()).isEqualTo(200)

        val respons2 = httpClient.send(request, BodyHandlers.ofString())
        assertThat(respons2.statusCode()).isEqualTo(200)

        wiremockServer.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo(tokenXWiremockUrl)))
        wiremockServer.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo(altinnProxyUrl)))
    }

    @Test
    fun `Bruker ikke cache når Altinn returnerer tom liste av organisasjoner`() {
        val tomListeAvOrganisasjoner = listOf<AltinnTilgang>()
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, tomListeAvOrganisasjoner)
        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val accessToken = hentToken(fødselsnummer)

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidatlister?virksomhetsnummer=987654321"))
            .header("Authorization", "Bearer $accessToken")
            .build()
        httpClient.send(request, BodyHandlers.ofString())

        httpClient.send(request, BodyHandlers.ofString())

        wiremockServer.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo(tokenXWiremockUrl)))
        wiremockServer.verify(2, WireMock.postRequestedFor(WireMock.urlEqualTo(altinnProxyUrl)))
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
            Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummer),
        )
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val request = HttpRequest.newBuilder(URI(endepunkt))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)

        val kandidatlisteMedKandidaterJson =
            defaultObjectMapper.readTree(response.body())
        assertThat(kandidatlisteMedKandidaterJson).isEmpty()
    }
}
