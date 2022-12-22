package no.nav.arbeidsgiver.toi.presentertekandidater.samtykke

import javax.sql.DataSource

class SamtykkeRepository(private val dataSource: DataSource) {

    fun lagre(fødselsnummer: String) {
        dataSource.connection.use {
            val sql = """
                insert into samtykke(fødselsnummer, opprettet) 
                values (?, current_timestamp)
            """.trimIndent()

            it.prepareStatement(sql).apply {
                this.setString(1, fødselsnummer)
            }.use { s-> s.execute() }
        }
    }

    fun harSamtykket(fødselsnummer: String): Boolean =
        dataSource.connection.use {
            val sql = """
                select 1 from samtykke where fødselsnummer = ?
            """.trimIndent()

            it.prepareStatement(sql).apply {
                this.setString(1, fødselsnummer)
            }.use { s-> s.executeQuery().next() }
        }
}