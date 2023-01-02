package no.nav.arbeidsgiver.toi.presentertekandidater.statistikk

import javax.sql.DataSource

class StatistikkRepository(private val dataSource: DataSource) {


    fun antallKandidatlister(): Long {
        dataSource.connection.use { connection ->
            val sql = """
                select count(*) from kandidatliste where slettet = false
            """.trimIndent()

            connection.prepareStatement(sql).use { s ->
                val rs = s.executeQuery()
                if (rs.next())
                    return rs.getLong(1)
                else
                    return 0
            }
        }
    }

    fun antallUnikeKandidater(): Long {
        dataSource.connection.use { connection ->
            val sql = """
                select count(distinct k.aktør_id)
                from kandidat k, kandidatliste l
                where 
                k.kandidatliste_id = l.id and
                l.slettet = false
            """.trimIndent()

            connection.prepareStatement(sql).use { s ->
                val rs = s.executeQuery()
                if (rs.next())
                    return rs.getLong(1)
                else
                    return 0
            }
        }
    }

    fun antallKandidatinnslag(): Long {
        dataSource.connection.use { connection ->
            val sql = """
                select count(k.aktør_id)
                from kandidat k, kandidatliste l
                where 
                k.kandidatliste_id = l.id and
                l.slettet = false
            """.trimIndent()

            connection.prepareStatement(sql).use { s ->
                val rs = s.executeQuery()
                if (rs.next())
                    return rs.getLong(1)
                else
                    return 0
            }
        }
    }

    fun antallKandidatinnslagMedVurdering(vurdering: String): Long {
        dataSource.connection.use { connection ->
            val sql = """
                select count(k.*)
                from kandidat k, kandidatliste l
                where 
                k.kandidatliste_id = l.id and
                l.slettet = false and
                k.arbeidsgivers_vurdering = ?
            """.trimIndent()

            connection.prepareStatement(sql).apply {
                this.setString(1, vurdering)
            }.use { s ->
                val rs = s.executeQuery()
                if (rs.next())
                    return rs.getLong(1)
                else
                    return 0
            }
        }
    }
}