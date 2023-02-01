package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import no.nav.arbeidsgiver.toi.presentertekandidater.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.ResultSet
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistrerVisningTest {
    private val httpClient = HttpClient.newHttpClient()
    private val wiremockServer = hentWiremock()
    private val kandidatlisteRepository = kandidatlisteRepositoryMedLokalPostgres()

    @BeforeAll
    fun init() {
        startLocalApplication()
    }

    @AfterEach
    fun slettDB() {
        slettAltIDatabase()
    }

    @Test
    fun `Skal oppdatere registrering av visning og returnere 200 OK`() {
        val kandidatliste = kandidatlisteRepository.lagre(Testdata.kandidatliste())
        val kandidat = kandidatlisteRepository.lagre(Testdata.lagKandidatTilKandidatliste(kandidatliste.id!!))
        val organisasjoner = listOf(Testdata.lagAltinnOrganisasjon("Et Navn", kandidatliste.virksomhetsnummer))
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val accessToken = hentToken(fødselsnummer)

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidat/${kandidat.uuid}/registrerviskontaktinfo"))
            .header("Authorization", "Bearer $accessToken")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)
        val databaseRader = hentDatabaseRader(kandidat.aktørId)
        assertTrue(databaseRader.next())
        assertThat(databaseRader.getString("stilling_id")).isEqualTo(kandidatliste.stillingId.toString())
        assertFalse(databaseRader.next())
    }

    @Test
    fun `Skal returnere 400 om man prøver registrere visning av kandidat som ikke finnes`() {
        val organisasjoner = listOf(Testdata.lagAltinnOrganisasjon("Et Navn", tilfeldigVirksomhetsnummer()))
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val accessToken = hentToken(fødselsnummer)

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidat/${UUID.randomUUID()}/registrerviskontaktinfo"))
            .header("Authorization", "Bearer $accessToken")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(400)
    }
}

private fun hentDatabaseRader(aktørId: String): ResultSet {
    dataSource.connection.also {
        val sql = """
                select * from visning_kontaktinfo where aktør_id = ?
            """.trimIndent()

        it.prepareStatement(sql).apply {
            this.setString(1, aktørId)
        }.also { statement -> return statement.executeQuery() }
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistrerVisningFeilendeDBTest {
    private val httpClient = HttpClient.newHttpClient()
    private val wiremockServer = hentWiremock()
    private val kandidatlisteRepository = kandidatlisteRepositoryMedLokalPostgres()
    private lateinit var logWatcher: ListAppender<ILoggingEvent>

    @BeforeAll
    fun init() {
        startLocalApplication()
        setUpLogWatcher()
    }

    @BeforeEach
    fun setUp() {
        slettAltIDatabase()
    }

    private fun setUpLogWatcher() {
        logWatcher = ListAppender<ILoggingEvent>()
        logWatcher.start()
        val logger =
            LoggerFactory.getLogger("controller") as ch.qos.logback.classic.Logger
        logger.addAppender(logWatcher)
    }

    @Test
    fun `Skal returnere 200 selv om registrering av visning feiler`() {
        val kandidatliste = kandidatlisteRepository.lagre(Testdata.kandidatliste())
        val kandidat = kandidatlisteRepository.lagre(Testdata.lagKandidatTilKandidatliste(kandidatliste.id!!, aktørId = "987"))
        val organisasjoner = listOf(Testdata.lagAltinnOrganisasjon("Et Navn", kandidatliste.virksomhetsnummer))
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val accessToken = hentToken(fødselsnummer)

        val request = HttpRequest.newBuilder(URI("http://localhost:9000/kandidat/${kandidat.uuid}/registrerviskontaktinfo"))
            .header("Authorization", "Bearer $accessToken")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)
        val databaseRader = hentDatabaseRader(kandidat.aktørId)
        assertFalse(databaseRader.next())

        assertThat(logWatcher.list).isNotEmpty
        assertThat(logWatcher.list[logWatcher.list.size - 1].message).contains("Fikk ikke til å lagre visning av kontakinfo med kandidatuuid: ${kandidat.uuid}")
    }
}
