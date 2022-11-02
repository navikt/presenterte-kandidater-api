package no.nav.arbeidsgiver.toi.presentertekandidater

import org.flywaydb.core.Flyway
import java.util.UUID
import javax.sql.DataSource

class Repository(private val dataSource: DataSource) {
    fun lagre(kandidatliste: Kandidatliste) {
        dataSource.connection.use {
            val sql = """
                insert into kandidatliste(
                    stilling_id,
                    tittel,
                    status,
                    slettet,
                    virksomhetsnummer
                ) values (?, ?, ?, ?, ?)
            """.trimIndent()

            it.prepareStatement(sql).apply {
                this.setObject(1, kandidatliste.stillingId)
                this.setString(2, kandidatliste.tittel)
                this.setString(3, kandidatliste.status)
                this.setBoolean(4, kandidatliste.slettet)
                this.setString(5, kandidatliste.virksomhetsnummer)
            }.execute()
        }
    }

    fun kjørFlywayMigreringer() {
        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
    }
}
