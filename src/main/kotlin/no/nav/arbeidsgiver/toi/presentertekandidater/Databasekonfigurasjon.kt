package no.nav.arbeidsgiver.toi.presentertekandidater

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

class Databasekonfigurasjon(env: Map<String, String>) {
    private val host = env.variable("NAIS_DATABASE_PRESENTERTE_KANDIDATER_API_PRESENTERTE_KANDIDATER_DB_HOST")
    private val port = env.variable("NAIS_DATABASE_PRESENTERTE_KANDIDATER_API_PRESENTERTE_KANDIDATER_DB_PORT")
    private val database = env.variable("NAIS_DATABASE_PRESENTERTE_KANDIDATER_API_PRESENTERTE_KANDIDATER_DB_DATABASE")
    private val user = env.variable("NAIS_DATABASE_PRESENTERTE_KANDIDATER_API_PRESENTERTE_KANDIDATER_DB_USERNAME")
    private val pw = env.variable("NAIS_DATABASE_PRESENTERTE_KANDIDATER_API_PRESENTERTE_KANDIDATER_DB_PASSWORD")

    fun lagDatasource() = HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://$host:$port/$database"
        minimumIdle = 1
        maximumPoolSize = 2
        driverClassName = "org.postgresql.Driver"
        initializationFailTimeout = 5000
        username = user
        password = pw
        validate()
    }.let(::HikariDataSource)
}