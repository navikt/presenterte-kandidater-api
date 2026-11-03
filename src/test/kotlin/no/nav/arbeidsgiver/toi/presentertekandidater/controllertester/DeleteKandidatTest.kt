package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.ZonedDateTime
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeleteKandidatTest {
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    private val httpClient = HttpClient.newHttpClient()
    private val wiremockServer = hentWiremock()

    @BeforeAll
    fun init() {
        startLocalApplication()
    }

    @Test
    fun `Skal slette kandidat og returnere 200 OK`() {
        val virksomhetsnummer = "987654321"
        val stillingId = UUID.randomUUID()
        val kandidatliste = kandidatliste().copy(
            virksomhetsnummer = virksomhetsnummer,
            stillingId = stillingId
        )
        repository.lagre(kandidatliste)
        val lagretKandidatliste = repository.hentKandidatliste(stillingId)!!
        val kandidat = Kandidat(
            uuid = UUID.randomUUID(),
            aktørId = "dummy",
            kandidatlisteId = lagretKandidatliste.id!!,
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = ZonedDateTime.now()
        )
        repository.lagre(kandidat)
        val organisasjoner = listOf(Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummer))
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidat/${kandidat.uuid}"))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .DELETE()
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)
        assertNull(repository.hentKandidat(kandidat.aktørId, kandidat.kandidatlisteId))
    }

    @Test
    fun `Skal returnere 403 når man ikke representerer virksomheten`() {
        val virksomhetsnummerManIkkeHarRettighetTil = "123456789"
        val virksomhetsnummerManHarRettighetTil = "987654321"
        val stillingId = UUID.randomUUID()
        val kandidatliste = kandidatliste().copy(
            virksomhetsnummer = virksomhetsnummerManIkkeHarRettighetTil,
            stillingId = stillingId
        )
        repository.lagre(kandidatliste)
        val lagretKandidatliste = repository.hentKandidatliste(stillingId)!!
        val kandidat = Kandidat(
            uuid = UUID.randomUUID(),
            aktørId = "dummy",
            kandidatlisteId = lagretKandidatliste.id!!,
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = ZonedDateTime.now()
        )
        repository.lagre(kandidat)
        val organisasjoner = listOf(Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummerManHarRettighetTil))
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidat/${kandidat.uuid}"))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .DELETE()
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(403)
        val jsonbody = response.body()
        assertThat(jsonbody.isEmpty())
    }

    @Test
    fun `Skal ikke kunne slette kandidat hvis man ikke har samtykket til vilkår`() {
        val virksomhetsnummer = "987654321"
        val stillingId = UUID.randomUUID()
        val kandidatliste = kandidatliste().copy(
            virksomhetsnummer = virksomhetsnummer,
            stillingId = stillingId
        )
        repository.lagre(kandidatliste)
        val lagretKandidatliste = repository.hentKandidatliste(stillingId)!!
        val kandidat = Kandidat(
            uuid = UUID.randomUUID(),
            aktørId = "dummy",
            kandidatlisteId = lagretKandidatliste.id!!,
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = ZonedDateTime.now()
        )
        repository.lagre(kandidat)
        val organisasjoner = listOf(Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummer))
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidat/${kandidat.uuid}"))
            .header("Authorization", "Bearer ${hentToken(tilfeldigFødselsnummer())}")
            .DELETE()
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(451)
        assertNotNull(repository.hentKandidat(kandidat.aktørId, kandidat.kandidatlisteId))
    }

    @Test
    fun `Skal få 400 bad request hvis kandidaten ikke eksisterer`() {
        val virksomhetsnummer = "987654321"
        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val organisasjoner = listOf(Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummer))
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        val kandidatUuidSomIkkeFinnes = UUID.randomUUID()

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidat/${kandidatUuidSomIkkeFinnes}"))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .DELETE()
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(400)
    }
}
