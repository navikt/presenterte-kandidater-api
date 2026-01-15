package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnReportee
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnTilgang
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidatliste
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetKandidatlisterTest {
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    private val fuel = FuelManager()
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
        val (_, response) = fuel
            .get(endepunkt)
            .response()

        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `Svarer 401 Unauthorized hvis forespørselens token er ugyldig`() {
        val endepunkt = "http://localhost:9000/kandidatlister"
        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentUgyldigToken())
            .response()

        assertThat(response.statusCode).isEqualTo(401)
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
        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(fødselsnummer))
            .response()

        assertThat(response.statusCode).isEqualTo(400)
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

        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(fødselsnummer))
            .response()

        assertThat(response.statusCode).isEqualTo(451)
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
            Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummer),
        )
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(fødselsnummer))
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
            Assertions.within(3, ChronoUnit.SECONDS)
        )
        assertThat(ZonedDateTime.parse(kandidatlisteJson["opprettet"].textValue())).isCloseTo(
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
        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(fødselsnummer))
            .response()

        assertThat(response.statusCode).isEqualTo(200)
        val kandidatlisterMedKandidaterJson =
            defaultObjectMapper.readTree(response.body().asString("application/json;charset=utf-8"))
        val opprettetDatoFørsteListe =
            ZonedDateTime.parse(kandidatlisterMedKandidaterJson[0]["kandidatliste"]["opprettet"].textValue())
        val opprettetDatoAndreListe =
            ZonedDateTime.parse(kandidatlisterMedKandidaterJson[1]["kandidatliste"]["opprettet"].textValue())
        val opprettetDatoTredjeListe =
            ZonedDateTime.parse(kandidatlisterMedKandidaterJson[2]["kandidatliste"]["opprettet"].textValue())
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
        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(fødselsnummer))
            .response()

        assertThat(response.statusCode).isEqualTo(403)
        val jsonbody = response.body().asString("application/json;charset=utf-8")
        assertThat(jsonbody.isEmpty())
    }

    @Test
    fun `Returnerer 503 når Altinn feiler`() {
        stubHttpStatus500FraAltinnProxy(wiremockServer)
        val (_, response) = fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=987654321")
            .authentication().bearer(hentToken(tilfeldigFødselsnummer()))
            .response()
        assertThat(response.statusCode).isEqualTo(503)
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

        val (_, respons1, _) = fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=987654321")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()
        assertThat(respons1.statusCode).isEqualTo(200)

        val (_, respons2, _) = fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=987654321")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()
        assertThat(respons2.statusCode).isEqualTo(200)

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

        fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=987654321")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()

        fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=987654321")
            .authentication().bearer(accessToken)
            .responseObject<List<AltinnReportee>>()

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
        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(fødselsnummer))
            .response()

        assertThat(response.statusCode).isEqualTo(200)

        val kandidatlisteMedKandidaterJson =
            defaultObjectMapper.readTree(response.body().asString("application/json;charset=utf-8"))
        assertThat(kandidatlisteMedKandidaterJson).isEmpty()
    }
}
