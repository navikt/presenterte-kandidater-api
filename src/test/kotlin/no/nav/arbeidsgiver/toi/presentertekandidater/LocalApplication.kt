package no.nav.arbeidsgiver.toi.presentertekandidater

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.javalin.Javalin
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.security.token.support.core.configuration.IssuerProperties
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.net.URL

fun main() {
    startLocalApplication()
}

val issuerProperties = mapOf(
    Rolle.ARBEIDSGIVER to IssuerProperties(
        URL("http://localhost:18301/default/.well-known/openid-configuration"),
        listOf("default"),
        "tokenX"
    ),
)

fun startLocalApplication(javalin: Javalin = opprettJavalinMedTilgangskontroll(issuerProperties)) {
    opprettTestRepositoryMedLokalPostgres()
    val rapid = TestRapid()
    startApp(javalin, rapid) { true }
}

fun opprettTestRepositoryMedLokalPostgres(): Repository {
    var postgres = PostgreSQLContainer(DockerImageName.parse("postgres:14.4-alpine"))
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
