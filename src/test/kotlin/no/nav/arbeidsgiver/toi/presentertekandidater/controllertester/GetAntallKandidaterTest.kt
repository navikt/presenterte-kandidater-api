package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.lagKandidatTilKandidatliste
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetAntallKandidaterTest {
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    private val httpClient = HttpClient.newHttpClient()
    private val wiremockServer = hentWiremock()
    private val endepunkt = "http://localhost:9000/ekstern/antallkandidater"
    fun endepunkt(virksomhetsnummer: String) = "$endepunkt?virksomhetsnummer=$virksomhetsnummer"

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
        val request = HttpRequest.newBuilder(URI(endepunkt))
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(401)
    }

    @Test
    fun `Svarer 401 Unauthorized hvis forespørselens token er ugyldig`() {
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
        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val request = HttpRequest.newBuilder(URI(endepunkt))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(400)
    }

    @Test
    fun `Skal kunne hente ut kandidater selv om man ikke har samtykket til vilkår`() {
        val stillingId = UUID.randomUUID()
        val virksomhetsnummer = "98435243"
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

        val request = HttpRequest.newBuilder(URI(endepunkt(virksomhetsnummer)))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)
        val kandidatlisteMedKandidaterJson =
            response.body()
        assertThat(kandidatlisteMedKandidaterJson).isEqualTo("""{"antallKandidater":0}""")
    }

    @Test
    fun `Returnerer antall kandidater 0 dersom det ikke finnes kandidater knyttet til virksomhetsnummer`() {
        val stillingId = UUID.randomUUID()
        val virksomhetsnummer = "323534343"
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
        val request = HttpRequest.newBuilder(URI(endepunkt(virksomhetsnummer)))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)

        val kandidatlisteMedKandidaterJson =
            response.body()

        assertThat(kandidatlisteMedKandidaterJson).isEqualTo("""{"antallKandidater":0}""")
    }

    @Test
    fun `Returnerer antall kandidater 0 dersom det ikke finnes kandidatlister knyttet til virksomhetsnummer`() {
        val virksomhetsnummer = "323534343"

        val organisasjoner = listOf(
            Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummer),
        )
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val request = HttpRequest.newBuilder(URI(endepunkt(virksomhetsnummer)))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)

        val kandidatlisteMedKandidaterJson =
            response.body()

        assertThat(kandidatlisteMedKandidaterJson).isEqualTo("""{"antallKandidater":0}""")
    }

    @Test
    fun `Returnerer antall kandidater knyttet til virksomhetsnummer`() {
        val stillingId = UUID.randomUUID()
        val virksomhetsnummer = "123123123"

        repository.lagre(
            kandidatliste().copy(
                virksomhetsnummer = virksomhetsnummer,
                stillingId = stillingId
            )
        )
        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat = lagKandidatTilKandidatliste(kandidatliste?.id!!)
        repository.lagre(kandidat)
        val organisasjoner = listOf(
            Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummer),
        )
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val request = HttpRequest.newBuilder(URI(endepunkt(virksomhetsnummer)))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)

        val kandidatlisteMedKandidaterJson =
            response.body()

        assertThat(kandidatlisteMedKandidaterJson).isEqualTo("""{"antallKandidater":1}""")
    }

    @Test
    fun `Returnerer 403 når man henter ut antall kandidater for annet virksomhetsnummer enn man representerer`() {
        val virksomhetsnummerManIkkeRepresenterer = tilfeldigVirksomhetsnummer()
        val virksomhetsnummerManRepresenterer = tilfeldigVirksomhetsnummer()

        val organisasjoner = listOf(
            Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummerManRepresenterer),
        )
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val request = HttpRequest.newBuilder(URI(endepunkt(virksomhetsnummerManIkkeRepresenterer)))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(403)
    }
}
