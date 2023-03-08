package no.nav.arbeidsgiver.toi.presentertekandidater.visningkontaktinfo

import java.sql.ResultSet
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import javax.sql.DataSource

class VisningKontaktinfoRepository(private val dataSource: DataSource) {

    fun registrerVisning(aktørId: String, stillingId: UUID) = dataSource.connection.use {
        it.prepareStatement(
            "insert into visning_kontaktinfo (aktør_id, stilling_id)" +
                    " values(?, ?)"
        ).apply {
            setString(1, aktørId)
            setObject(2, stillingId)
        }.executeUpdate() > 0
    }

    fun hentAlleRegistrerteVisninger(): List<RegistrertVisning>  = dataSource.connection.use {
        val resultSet = it.prepareStatement("select * from visning_kontaktinfo")
            .executeQuery()

        return generateSequence {
            if (resultSet.next()) {
                RegistrertVisning.fraResultSet(resultSet)
            } else null
        }.toList()
    }

    fun markerSomPublisert(registrertVisning: RegistrertVisning) {
        dataSource.connection.use {
            it.prepareStatement("""
                update visning_kontaktinfo
                set publisert_melding = true
                where id = ?
            """.trimIndent()).apply {
                setLong(1, registrertVisning.id)
            }.use { statement -> statement.executeUpdate() }
        }
    }

    fun gjørOperasjonPåAlleUpubliserteVisninger(operasjon: (RegistrertVisning, Int) -> Unit) {
        val connection = dataSource.connection

        connection.autoCommit = false

        connection.let {
            val resultSet = it.prepareStatement(
                """
                select * from visning_kontaktinfo
                where publisert_melding is not true
            """.trimIndent(),
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY
            ).also { stmt -> stmt.fetchSize = 100 }
            .executeQuery()

            resultSet.forEachRowIndexed { resultSetRow, index ->
                val registrertVisning = RegistrertVisning.fraResultSet(resultSetRow)
                operasjon(registrertVisning, index)
            }
        }

        connection.commit()
    }

    private fun ResultSet.forEachRowIndexed(operation: (ResultSet, Int) -> Unit) {
        var teller = 0

        while (this.next()) {
            operation(this, teller++)
        }
    }

    data class RegistrertVisning(
        val id: Long,
        val aktørId: String,
        val stillingsId: UUID,
        val tidspunkt: ZonedDateTime,
        val publisertMelding: Boolean
    ) {
        companion object {
            fun fraResultSet(resultSet: ResultSet) =
                RegistrertVisning(
                    id = resultSet.getLong("id"),
                    aktørId = resultSet.getString("aktør_id"),
                    stillingsId = resultSet.getObject("stilling_id") as UUID,
                    tidspunkt = resultSet.getTimestamp("tidspunkt").toInstant().atZone(ZoneId.of("Europe/Oslo")),
                    publisertMelding = resultSet.getBoolean("publisert_melding")
                )
        }
    }
}