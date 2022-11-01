package no.nav.arbeidsgiver.toi.presentertekandidater

import org.flywaydb.core.Flyway
import javax.sql.DataSource

class Repository(private val dataSource: DataSource) {
    fun kjørFlywayMigreringer() {
        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
    }
}
