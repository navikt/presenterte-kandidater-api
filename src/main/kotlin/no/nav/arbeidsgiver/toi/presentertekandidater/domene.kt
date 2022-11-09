package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.annotation.JsonIgnore
import io.ktor.server.util.*
import java.math.BigInteger
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

data class Kandidatliste(
    @JsonIgnore
    val id: BigInteger? = null,
    val uuid: UUID,
    val stillingId: UUID,
    val tittel: String,
    val status: Status,
    val slettet: Boolean = false,
    val virksomhetsnummer: String,
    val sistEndret: ZonedDateTime
) {
    companion object {

        fun fraDatabase(rs: ResultSet) = Kandidatliste(
            id = rs.getBigDecimal("id").toBigInteger(),
            uuid = rs.getObject("uuid") as UUID,
            stillingId = rs.getObject("stilling_id") as UUID,
            tittel = rs.getString("tittel"),
            status = Status.valueOf(rs.getString("status")),
            slettet = rs.getBoolean("slettet"),
            virksomhetsnummer = rs.getString("virksomhetsnummer"),
            sistEndret = rs.getTimestamp("sist_endret").toInstant().atZone(ZoneId.of("Europe/Oslo"))
        )
    }
    enum class Status {
        ÅPEN, LUKKET
    }
}


data class KandidatlisteMedAntallKandidater(
    val kandidatliste: Kandidatliste,
    val antallKandidater: Int
)

data class KandidatlisteMedKandidat(
    @JsonIgnore
    val id: BigInteger? = null,
    val uuid: UUID,
    val stillingId: UUID,
    val tittel: String,
    val status: String,
    val slettet: Boolean = false,
    val virksomhetsnummer: String,
    val kandidater: List<Kandidat>
)

data class Kandidat(
    @JsonIgnore
    val id: BigInteger? = null,
    val uuid: UUID,
    val aktørId: String,
    @JsonIgnore
    val kandidatlisteId: BigInteger,
) {
    companion object {
        fun fraDatabase(rs: ResultSet): Kandidat {
            return Kandidat(
                id = rs.getBigDecimal("id").toBigInteger(),
                uuid = rs.getObject("uuid") as UUID,
                aktørId = rs.getString("aktør_id"),
                kandidatlisteId = rs.getBigDecimal("kandidatliste_id").toBigInteger(),
            )
        }
    }

    enum class ArbeidsgiversVurdering {
        AKTUELL,
        TIL_VURDERING,
        IKKE_AKTUELL,
        FÅTT_JOBBEN,
    }
}
