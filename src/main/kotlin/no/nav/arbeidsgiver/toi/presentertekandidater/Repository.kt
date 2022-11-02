package no.nav.arbeidsgiver.toi.presentertekandidater

import org.flywaydb.core.Flyway
import java.sql.Timestamp
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

    fun lagre(kandidat: Kandidat) {
        dataSource.connection.use {
            val sql = """
                insert into kandidat(
                    aktør_id,
                    kandidatliste_id,
                    hendelsestidspunkt,
                    hendelsestype,
                    arbeidsgivers_status
                ) values (?, ?, ?, ?, ?)
            """.trimIndent()

            it.prepareStatement(sql).apply {
                this.setObject(1, kandidat.aktørId)
                this.setObject(2, kandidat.kandidatlisteId)
                this.setTimestamp(3, Timestamp.valueOf(kandidat.hendelsestidspunkt))
                this.setString(4, kandidat.hendelsestype)
                this.setString(5, kandidat.arbeidsgiversStatus)
            }.execute()
        }
    }

    fun hentKandidatliste(stillingId: UUID): Kandidatliste? {
        dataSource.connection.use {
            val resultSet = it.prepareStatement("select * from kandidatliste where stilling_id = ?").apply {
                this.setObject(1, stillingId)
            }.executeQuery()

            if (!resultSet.next()) {
                return null
            }

            return Kandidatliste.fraDatabase(resultSet)
        }
    }


    fun kjørFlywayMigreringer() {
        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
    }

    fun hentKandidat(aktørId: String): Kandidat? {
        dataSource.connection.use {
            val resultSet = it.prepareStatement("select * from kandidat where aktør_id = ?").apply {
                this.setObject(1, aktørId)
            }.executeQuery()

            if (!resultSet.next()) {
                return null
            }

            return Kandidat.fraDatabase(resultSet)
        }
    }
}

