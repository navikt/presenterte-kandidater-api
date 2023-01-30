package no.nav.arbeidsgiver.toi.presentertekandidater.visningkontaktinfo

import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidatliste
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
}