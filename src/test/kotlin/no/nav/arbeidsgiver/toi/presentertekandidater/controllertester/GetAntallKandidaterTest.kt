package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.extensions.authentication
import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.lagKandidatTilKandidatliste
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetAntallKandidaterTest {
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    private val fuel = FuelManager()
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
        val (_, response) = fuel
            .get(endepunkt)
            .response()

        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `Svarer 401 Unauthorized hvis forespørselens token er ugyldig`() {
        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentUgyldigToken())
            .response()

        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `Svarer 400 Bad Request hvis URL-en ikke inneholder virksomhetsnummer`() {
        val organisasjoner = listOf(
            Testdata.lagAltinnTilgang("Et Navn", "123456789"),
            Testdata.lagAltinnTilgang("Et Navn", "987654321"),
        )
        stubHentingAvTilgangerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)
        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(fødselsnummer))
            .response()

        assertThat(response.statusCode).isEqualTo(400)
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
            Testdata.lagAltinnTilgang("Et Navn", virksomhetsnummer),
        )
        stubHentingAvTilgangerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()

        val (_, response) = fuel
            .get(endepunkt(virksomhetsnummer))
            .authentication().bearer(hentToken(fødselsnummer))
            .response()

        assertThat(response.statusCode).isEqualTo(200)
        val kandidatlisteMedKandidaterJson =
            response.body().asString("application/json;charset=utf-8")
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
            Testdata.lagAltinnTilgang("Et Navn", virksomhetsnummer),
        )
        stubHentingAvTilgangerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val (_, response) = fuel
            .get(endepunkt(virksomhetsnummer))
            .authentication().bearer(hentToken(fødselsnummer))
            .response()

        assertThat(response.statusCode).isEqualTo(200)

        val kandidatlisteMedKandidaterJson =
            response.body().asString("application/json;charset=utf-8")

        assertThat(kandidatlisteMedKandidaterJson).isEqualTo("""{"antallKandidater":0}""")
    }

    @Test
    fun `Returnerer antall kandidater 0 dersom det ikke finnes kandidatlister knyttet til virksomhetsnummer`() {
        val virksomhetsnummer = "323534343"

        val organisasjoner = listOf(
            Testdata.lagAltinnTilgang("Et Navn", virksomhetsnummer),
        )
        stubHentingAvTilgangerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val (_, response) = fuel
            .get(endepunkt(virksomhetsnummer))
            .authentication().bearer(hentToken(fødselsnummer))
            .response()

        assertThat(response.statusCode).isEqualTo(200)

        val kandidatlisteMedKandidaterJson =
            response.body().asString("application/json;charset=utf-8")

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
            Testdata.lagAltinnTilgang("Et Navn", virksomhetsnummer),
        )
        stubHentingAvTilgangerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val (_, response) = fuel
            .get(endepunkt(virksomhetsnummer))
            .authentication().bearer(hentToken(fødselsnummer))
            .response()

        assertThat(response.statusCode).isEqualTo(200)

        val kandidatlisteMedKandidaterJson =
            response.body().asString("application/json;charset=utf-8")

        assertThat(kandidatlisteMedKandidaterJson).isEqualTo("""{"antallKandidater":1}""")
    }

    @Test
    fun `Returnerer 403 når man henter ut antall kandidater for annet virksomhetsnummer enn man representerer`() {
        val virksomhetsnummerManIkkeRepresenterer = tilfeldigVirksomhetsnummer()
        val virksomhetsnummerManRepresenterer = tilfeldigVirksomhetsnummer()

        val organisasjoner = listOf(
            Testdata.lagAltinnTilgang("Et Navn", virksomhetsnummerManRepresenterer),
        )
        stubHentingAvTilgangerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val (_, response) = fuel
            .get(endepunkt(virksomhetsnummerManIkkeRepresenterer))
            .authentication().bearer(hentToken(fødselsnummer))
            .response()

        assertThat(response.statusCode).isEqualTo(403)
    }
}
