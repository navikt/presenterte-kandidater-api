package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.tomakehurst.wiremock.WireMockServer
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.KandidatlisteRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.opensearch.OpenSearchKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.samtykke.SamtykkeRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.TokendingsKlient
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

fun main() {
    startLocalApplication()
}

val lokalPostgres: PostgreSQLContainer<*>
    get() {
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:14.4-alpine"))
            .withDatabaseName("dbname")
            .withUsername("username")
            .withPassword("pwd")

        postgres.start()
        return postgres
    }

private const val wiremockPort = 2022

private val wiremock = WireMockServer(wiremockPort).also {
    it.start()
}

fun hentWiremock(): WireMockServer {
    wiremock.stop()
    wiremock.start()
    return wiremock
}

val dataSource = HikariConfig().apply {
    jdbcUrl = lokalPostgres.jdbcUrl
    minimumIdle = 1
    maximumPoolSize = 2
    driverClassName = "org.postgresql.Driver"
    initializationFailTimeout = 5000
    username = lokalPostgres.username
    password = lokalPostgres.password
    validate()
}.let(::HikariDataSource)

fun kandidatlisteRepositoryMedLokalPostgres(): KandidatlisteRepository {
    try {
        slettAltIDatabase()
    } catch (e: Exception) {
        println("Trenger ikke slette fordi db-skjema ikke opprettet ennå")
    }
    return KandidatlisteRepository(dataSource)
}

fun samtykkeRepositoryMedLokalPostgres(): SamtykkeRepository {
    try {
        slettAltIDatabase()
    } catch (e: Exception) {
        println("Trenger ikke slette fordi db-skjema ikke opprettet ennå")
    }
    return SamtykkeRepository(dataSource)
}

fun openSearchKlient() = OpenSearchKlient(envs)

fun slettAltIDatabase() {
    val connection = dataSource.connection

    connection.use {
        it.prepareStatement("delete from kandidat").execute()
        it.prepareStatement("delete from kandidatliste").execute()
        it.prepareStatement("delete from samtykke").execute()
    }
}

private val envs = mapOf(
    "OPEN_SEARCH_URI" to "http://localhost:${wiremockPort}",
    "OPEN_SEARCH_USERNAME" to "gunnar",
    "OPEN_SEARCH_PASSWORD" to "xyz",
    "NAIS_APP_NAME" to "min-app",
    "ALTINN_PROXY_URL" to "http://localhost:$wiremockPort/altinn-proxy-url",
    "ALTINN_PROXY_AUDIENCE" to "din:app",
    "TOKEN_X_WELL_KNOWN_URL" to "http://localhost:18301/default/.well-known/openid-configuration",
    "TOKEN_X_TOKEN_ENDPOINT" to "http://localhost:$wiremockPort/token-x-token-endpoint",
    "TOKEN_X_PRIVATE_JWK" to Testdata.privateJwk,
    "TOKEN_X_CLIENT_ID" to "clientId",
    "TOKEN_X_ISSUER" to "tokenXissuer",
    "NAIS_CLUSTER_NAME" to "local"
)

private var harStartetApplikasjonen = false
private val testRapid = TestRapid()

fun startLocalApplication() {
    if (!harStartetApplikasjonen) {
        startMockOAuth2Server()
        val altinnKlient = AltinnKlient(envs, TokendingsKlient(envs))

        startApp(
            testRapid,
            dataSource,
            OpenSearchKlient(envs),
            { true },
            altinnKlient,
            envs
        )
        harStartetApplikasjonen = true
    }
}



