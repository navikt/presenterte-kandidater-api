package no.nav.arbeidsgiver.toi.presentertekandidater

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.javalin.Javalin
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.security.token.support.core.configuration.IssuerProperties
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.net.URL
import io.mockk.mockk
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.Rolle
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.TokendingsKlient

private val repository = opprettTestRepositoryMedLokalPostgres()

fun main() {
    val presenterteKandidaterService = PresenterteKandidaterService(repository)
    startLocalApplication(
        presenterteKandidaterService = presenterteKandidaterService,
        repository = repository,
        javalin = opprettJavalinMedTilgangskontroll(issuerProperties)
    )
}

val issuerProperties = mapOf(
    Rolle.ARBEIDSGIVER to IssuerProperties(
        URL("http://localhost:18301/default/.well-known/openid-configuration"),
        listOf("default"),
        "tokenX"
    ),
)

private val envs = mapOf(
    "OPEN_SEARCH_URI" to "uri",
    "OPEN_SEARCH_USERNAME" to "username",
    "OPEN_SEARCH_PASSWORD" to "password",
    "NAIS_APPLICATION_NAME" to "min-app",
    "ALTINN_PROXY_URL" to "http://localhost/proxy-url",
    "ALTINN_PROXY_AUDIENCE" to "din:app",
    "TOKEN_X_WELL_KNOWN_URL" to "http://localhost/tokenx"
)

fun startLocalApplication(
    javalin: Javalin,
    rapid: TestRapid = TestRapid(),
    presenterteKandidaterService: PresenterteKandidaterService = mockk<PresenterteKandidaterService>(),
    repository: Repository = opprettTestRepositoryMedLokalPostgres(),
    openSearchKlient: OpenSearchKlient = OpenSearchKlient(envs),
    altinnKlient: AltinnKlient = AltinnKlient(envs, TokendingsKlient(envs)::veksleInnToken)
) {
    startApp(javalin, rapid, presenterteKandidaterService, repository, openSearchKlient, altinnKlient) { true }
}

fun opprettTestRepositoryMedLokalPostgres(): Repository {
    val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:14.4-alpine"))
        .withDatabaseName("dbname")
        .withUsername("username")
        .withPassword("pwd")

    postgres.start()
    log("LocalApplication").info("Started Postgres ${postgres.jdbcUrl}")

    val repository = Repository(lagDatasource(postgres))
    repository.kjørFlywayMigreringer()

    return repository
}

fun lagDatasource(postgres: PostgreSQLContainer<*>) = HikariConfig().apply {
    jdbcUrl = postgres.jdbcUrl
    minimumIdle = 1
    maximumPoolSize = 2
    driverClassName = "org.postgresql.Driver"
    initializationFailTimeout = 5000
    username = postgres.username
    password = postgres.password
    validate()
}.let(::HikariDataSource)
