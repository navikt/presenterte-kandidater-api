package no.nav.arbeidsgiver.toi.presentertekandidater.statistikk
import javax.sql.DataSource

class StatistikkRepository (private val dataSource: DataSource) {

    fun antallKandidatlister(): Int {
        dataSource.connection.use { c ->
            val sql = """
                select count(*) from kandidatliste where slettet = false
            """.trimIndent()

            c.prepareStatement(sql).use { s->
                val rs = s.executeQuery()
                if (rs.next())
                    return rs.getInt(1)
                else
                    return 0
            }
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

            c.prepareStatement(sql).use { s ->
                val rs = s.executeQuery()
                if (rs.next())
                    return rs.getInt(1)
                else
                    return 0
            }
        }
    }
    fun antallKandidaterMedVurdering(vurdering: String): Int {
        dataSource.connection.use { c ->
            val sql = """
                select count(distinct k.aktør_id)
                from kandidat k, kandidatliste l
                where 
                k.kandidatliste_id = l.id and
                l.slettet = false and
                k.arbeidsgivers_vurdering = ?
            """.trimIndent()

            c.prepareStatement(sql).apply {
                this.setString(1, vurdering)
            }.use { s ->
                val rs = s.executeQuery()
                if (rs.next())
                    return rs.getInt(1)
                else
                    return 0
            }
        }
    }
}