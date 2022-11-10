package no.nav.arbeidsgiver.toi.presentertekandidater

import org.flywaydb.core.Flyway
import java.math.BigInteger
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class Repository(private val dataSource: DataSource) {
    fun lagre(kandidatliste: Kandidatliste) {
        dataSource.connection.use {
            val sql = """
                insert into kandidatliste(
                    stilling_id,
                    uuid,
                    tittel,
                    status,
                    slettet,
                    virksomhetsnummer,
                    sist_endret
                ) values (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            it.prepareStatement(sql).apply {
                this.setObject(1, kandidatliste.stillingId)
                this.setObject(2, kandidatliste.uuid)
                this.setString(3, kandidatliste.tittel)
                this.setString(4, kandidatliste.status.name)
                this.setBoolean(5, kandidatliste.slettet)
                this.setString(6, kandidatliste.virksomhetsnummer)
                this.setTimestamp(7, Timestamp(kandidatliste.sistEndret.toInstant().toEpochMilli()))
            }.execute()
        }
    }

    fun lagre(kandidat: Kandidat) {
        dataSource.connection.use {
            val sql = """
                insert into kandidat(
                    aktør_id,
                    kandidatliste_id,
                    uuid
                ) values (?, ?, ?)
            """.trimIndent()

            it.prepareStatement(sql).apply {
                this.setObject(1, kandidat.aktørId)
                this.setObject(2, kandidat.kandidatlisteId)
                this.setObject(3, kandidat.uuid)
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


    fun hentKandidatlisterMedAntall(virksomhetsnummer: String): List<KandidatlisteMedAntallKandidater> {
        dataSource.connection.use {
            val resultSet = it.prepareStatement(
                """
                |select kl.*,count(k.*) as antall
                |from kandidatliste kl 
                |left join kandidat k on k.kandidatliste_id = kl.id 
                |where  kl.virksomhetsnummer = ?
                |group by kl.id, kl.stilling_id, kl.tittel, kl.status, kl.slettet, kl.virksomhetsnummer
                |""".trimMargin()
            ).apply {
                this.setObject(1, virksomhetsnummer)
            }.executeQuery()

            return generateSequence {
                if (resultSet.next()) {
                    val kandidatliste = Kandidatliste.fraDatabase(resultSet)
                    val antall = resultSet.getInt("antall")
                    KandidatlisteMedAntallKandidater(kandidatliste = kandidatliste, antallKandidater = antall)
                } else null
            }.toList()
        }
    }

    fun hentKandidater(kandidatlisteId: BigInteger): List<Kandidat> {
        dataSource.connection.use {
            val resultSet = it.prepareStatement("select * from kandidat where kandidatliste_id = ?").apply {
                this.setObject(1, kandidatlisteId)
            }.executeQuery()

            return  generateSequence {
                if(resultSet.next()) {
                    Kandidat.fraDatabase(resultSet)
                } else null
            }.toList()
        }
    }

    fun hentKandidatlisteMedKandidater(stillingId: UUID): KandidatlisteMedKandidat? {
        val kandidatliste = hentKandidatliste(stillingId)

        if(kandidatliste?.id == null) {
            return null
        }

        val kandidater =  hentKandidater(kandidatliste.id)

        return KandidatlisteMedKandidat(
            id = kandidatliste.id,
            uuid = kandidatliste.uuid,
            stillingId = kandidatliste.stillingId,
            tittel = kandidatliste.tittel,
            status = kandidatliste.status,
            slettet = kandidatliste.slettet,
            virksomhetsnummer = kandidatliste.virksomhetsnummer,
            kandidater = kandidater
        )
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

