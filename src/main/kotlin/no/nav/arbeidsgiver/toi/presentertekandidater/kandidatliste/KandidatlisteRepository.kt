package no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste

import no.nav.arbeidsgiver.toi.presentertekandidater.log
import org.flywaydb.core.Flyway
import java.math.BigInteger
import java.sql.Timestamp
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

class KandidatlisteRepository(private val dataSource: DataSource) {
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
                    sist_endret,
                    opprettet
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            it.prepareStatement(sql).apply {
                this.setObject(1, kandidatliste.stillingId)
                this.setObject(2, kandidatliste.uuid)
                this.setString(3, kandidatliste.tittel)
                this.setString(4, kandidatliste.status.name)
                this.setBoolean(5, kandidatliste.slettet)
                this.setString(6, kandidatliste.virksomhetsnummer)
                this.setTimestamp(7, Timestamp(kandidatliste.sistEndret.toInstant().toEpochMilli()))
                this.setTimestamp(8, Timestamp(kandidatliste.opprettet.toInstant().toEpochMilli()))
            }.execute()
        }
    }

    fun oppdater(kandidatliste: Kandidatliste) {
        dataSource.connection.use {
            val sql = """
                update kandidatliste
                set tittel=?, status=?,slettet=?,virksomhetsnummer=?,sist_endret=?
                where stilling_id=?
            """.trimIndent()

            it.prepareStatement(sql).apply {
                this.setString(1, kandidatliste.tittel)
                this.setString(2, kandidatliste.status.name)
                this.setBoolean(3, kandidatliste.slettet)
                this.setString(4, kandidatliste.virksomhetsnummer)
                this.setTimestamp(5, Timestamp(kandidatliste.sistEndret.toInstant().toEpochMilli()))
                this.setObject(6, kandidatliste.stillingId)
            }.execute()
        }
    }

    fun lagre(kandidat: Kandidat) {
        dataSource.connection.use {
            val sql = """
                insert into kandidat(
                    aktør_id,
                    kandidatliste_id,
                    uuid,
                    arbeidsgivers_vurdering,
                    sist_endret
                ) values (?, ?, ?, ?, ?)
            """.trimIndent()

            it.prepareStatement(sql).apply {
                this.setObject(1, kandidat.aktørId)
                this.setObject(2, kandidat.kandidatlisteId)
                this.setObject(3, kandidat.uuid)
                this.setString(4, kandidat.arbeidsgiversVurdering.name)
                this.setTimestamp(5, Timestamp(kandidat.sistEndret.toInstant().toEpochMilli()))
            }.execute()
        }
    }

    fun hentKandidatliste(stillingId: UUID): Kandidatliste? {
        dataSource.connection.use {
            val sql = """
                select * from kandidatliste where stilling_id = ?
            """.trimIndent()

            val resultSet = it.prepareStatement(sql).apply {
                this.setObject(1, stillingId)
            }.executeQuery()

            if (!resultSet.next()) {
                return null
            }

            return Kandidatliste.fraDatabase(resultSet)
        }
    }

    fun hentKandidatlisteTilKandidat(kandidatUuid: UUID): Kandidatliste? {
        dataSource.connection.use {
            val sql = """
                select * from kandidatliste
                join kandidat on kandidatliste.id = kandidat.kandidatliste_id
                where kandidat.uuid = ?
            """.trimIndent()

            val resultSet = it.prepareStatement(sql).apply {
                this.setObject(1, kandidatUuid)
            }.executeQuery()

            if (!resultSet.next()) {
                return null
            }

            return Kandidatliste.fraDatabase(resultSet)
        }
    }

    fun hentKandidatlisterSomIkkeErSlettetMedAntall(virksomhetsnummer: String): List<KandidatlisteMedAntallKandidater> {
        dataSource.connection.use {
            val resultSet = it.prepareStatement(
                """
                |select kl.*,count(k.*) as antall
                |from kandidatliste kl 
                |left join kandidat k on k.kandidatliste_id = kl.id 
                |where  kl.virksomhetsnummer = ?
                |and kl.slettet = false
                |group by kl.id, kl.stilling_id, kl.tittel, kl.status, kl.slettet, kl.virksomhetsnummer
                |order by kl.opprettet desc
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

    fun hentTommeKandidatlisterSomIkkeErSlettetOgEldreEnn(dato: ZonedDateTime): List<Kandidatliste> {
        dataSource.connection.use {
            val resultSet = it.prepareStatement(
                """
                |select *
                |from kandidatliste kl 
                |where kl.slettet = false
                |and not exists (select 1 from kandidat where kandidatliste_id = kl.id)
                |and kl.sist_endret < ?
                |""".trimMargin()
            ).apply {
                this.setTimestamp(1, Timestamp(dato.toInstant().toEpochMilli()))
            }.executeQuery()

            return generateSequence {
                if (resultSet.next()) {
                    Kandidatliste.fraDatabase(resultSet)
                } else null
            }.toList()
        }
    }

    fun hentKandidaterSomIkkeErEndretSiden(dato: ZonedDateTime): List<Kandidat> {
        dataSource.connection.use {
            val resultSet = it.prepareStatement(
                """
                |select *
                |from kandidat k 
                |where k.sist_endret < ?
                |""".trimMargin()
            ).apply {
                this.setTimestamp(1, Timestamp(dato.toInstant().toEpochMilli()))
            }.executeQuery()

            return generateSequence {
                if (resultSet.next()) {
                    Kandidat.fraDatabase(resultSet)
                } else null
            }.toList()
        }
    }

    fun hentKandidater(kandidatlisteId: BigInteger): List<Kandidat> {
        dataSource.connection.use {
            val resultSet = it.prepareStatement("select * from kandidat where kandidatliste_id = ?").apply {
                this.setObject(1, kandidatlisteId)
            }.executeQuery()

            return generateSequence {
                if (resultSet.next()) {
                    Kandidat.fraDatabase(resultSet)
                } else null
            }.toList()
        }
    }

    fun hentKandidat(aktørId: String, kandidatlisteId: BigInteger): Kandidat? {
        dataSource.connection.use {
            val resultSet =
                it.prepareStatement("select * from kandidat where aktør_id = ? and kandidatliste_id = ?").apply {
                    this.setObject(1, aktørId)
                    this.setObject(2, kandidatlisteId)
                }.executeQuery()

            if (!resultSet.next()) {
                return null
            }

            return Kandidat.fraDatabase(resultSet)
        }
    }

    fun hentKandidatMedUUID(uuid: UUID): Kandidat? {
        dataSource.connection.use {
            val resultSet = it.prepareStatement("select * from kandidat where uuid = ?").apply {
                this.setObject(1, uuid)
            }.executeQuery()

            if (!resultSet.next()) {
                return null
            }
            return Kandidat.fraDatabase(resultSet)
        }
    }

    fun oppdaterArbeidsgiversVurdering(kandidatUuid: UUID, vurdering: Kandidat.ArbeidsgiversVurdering) : Boolean {
        return dataSource.connection.use {
            it.prepareStatement("update kandidat set arbeidsgivers_vurdering = ?, sist_endret = ? where uuid = ?").apply {
                this.setString(1, vurdering.name)
                this.setTimestamp(2, Timestamp(ZonedDateTime.now().toInstant().toEpochMilli()))
                this.setObject(3, kandidatUuid)
            }.executeUpdate().let {
                val bleOppdatert = it == 1
                log.info("${kandidatUuid} ble oppdatert med ${vurdering} : ${bleOppdatert}")
                bleOppdatert
            }
        }
    }

    fun markerKandidatlisteSomSlettet(stillingId: UUID) {
        return dataSource.connection.use {
            it.prepareStatement("update kandidatliste set slettet = true where stilling_id = ?").apply {
                this.setObject(1, stillingId)
            }.executeUpdate()
        }
    }

    fun lukkKandidatliste(stillingId: UUID) {
        return dataSource.connection.use {
            it.prepareStatement("update kandidatliste set status = ? where stilling_id = ?").apply {
                this.setObject(1, Kandidatliste.Status.LUKKET.name)
                this.setObject(2, stillingId)
            }.executeUpdate()
        }
    }

    fun slettKandidatFraKandidatliste(aktørId: String, kandidatlisteId: BigInteger) {
        return dataSource.connection.use {
            it.prepareStatement("delete from kandidat where aktør_id = ? and kandidatliste_id = ?").apply {
                this.setString(1, aktørId)
                this.setObject(2, kandidatlisteId)
            }.executeUpdate()
        }
    }

    fun slettKandidat(kandidatId: BigInteger) {
        return dataSource.connection.use {
            it.prepareStatement("delete from kandidat where id = ?").apply {
                this.setObject(1, kandidatId)
            }.executeUpdate()
        }
    }

    fun slettKandidat(kandidatUuid: UUID) {
        return dataSource.connection.use {
            it.prepareStatement("delete from kandidat where uuid = ?").apply {
                this.setObject(1, kandidatUuid)
            }.executeUpdate()
        }
    }
}
