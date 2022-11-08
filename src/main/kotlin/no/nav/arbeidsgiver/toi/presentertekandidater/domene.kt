package no.nav.arbeidsgiver.toi.presentertekandidater

import java.math.BigInteger
import java.sql.ResultSet
import java.time.LocalDateTime
import java.util.UUID

data class Kandidatliste(
    val id: BigInteger? = null,
    val stillingId: UUID,
    val tittel: String,
    val status: String,
    val slettet: Boolean = false,
    val virksomhetsnummer: String,
) {
    companion object {
        fun fraDatabase(rs: ResultSet): Kandidatliste {
            return Kandidatliste(
                id = rs.getBigDecimal("id").toBigInteger(),
                stillingId = rs.getObject("stilling_id") as UUID,
                tittel = rs.getString("tittel"),
                status = rs.getString("status"),
                slettet = rs.getBoolean("slettet"),
                virksomhetsnummer = rs.getString("virksomhetsnummer"),
            )
        }
    }
}

data class KandidatlisteMedAntallKandidater(
    val kandidatliste: Kandidatliste,
    val antallKandidater: Int)

data class Kandidat(
    val id: BigInteger? = null,
    val aktørId: String,
    val kandidatlisteId: BigInteger,
    val hendelsestidspunkt: LocalDateTime,
    val hendelsestype: String,
    val arbeidsgiversStatus: String,
) {
    companion object {
        fun fraDatabase(rs: ResultSet): Kandidat {
            return Kandidat(
                id = rs.getBigDecimal("id").toBigInteger(),
                aktørId = rs.getString("aktør_id"),
                kandidatlisteId = rs.getBigDecimal("kandidatliste_id").toBigInteger(),
                hendelsestidspunkt = rs.getTimestamp("hendelsestidspunkt").toLocalDateTime(),
                hendelsestype = rs.getString("hendelsestype"),
                arbeidsgiversStatus = rs.getString("arbeidsgivers_status"),
            )
        }
    }
}
