package no.nav.arbeidsgiver.toi.presentertekandidater.statistikk
import javax.sql.DataSource

class StatistikkRepository (private val dataSource: DataSource) {

    fun antallKandidatlister(): Int {
        dataSource.connection.use { c ->
            val sql = """
                select count(*) from kandidatliste where slettet = false
            """.trimIndent()

            val rs = c.prepareStatement(sql)
                .executeQuery()
            if (rs.next())
                return rs.getInt(1)
            else
                return 0
        }
    }

    fun antallKandidater(): Int {
        dataSource.connection.use { c ->
            val sql = """
                select count(distinct k.aktør_id)
                from kandidat k, kandidatliste l
                where 
                k.kandidatliste_id = l.id and
                l.slettet = false
            """.trimIndent()

            val rs = c.prepareStatement(sql)
                .executeQuery()
            if (rs.next())
                return rs.getInt(1)
            else
                return 0
        }
    }

}