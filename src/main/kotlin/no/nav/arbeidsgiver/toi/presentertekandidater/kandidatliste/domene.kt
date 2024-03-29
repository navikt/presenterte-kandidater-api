package no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigInteger
import java.sql.ResultSet
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

data class Kandidatliste(
    @JsonIgnore
    val id: BigInteger? = null,
    val uuid: UUID,
    val stillingId: UUID,
    val tittel: String,
    val status: Status,
    val slettet: Boolean = false,
    val virksomhetsnummer: String,
    val sistEndret: ZonedDateTime,
    val opprettet: ZonedDateTime,
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
            sistEndret = rs.getTimestamp("sist_endret").toInstant().atZone(ZoneId.of("Europe/Oslo")),
            opprettet = rs.getTimestamp("opprettet").toInstant().atZone(ZoneId.of("Europe/Oslo"))
        )

        fun ny(stillingId: UUID, tittel: String, virksomhetsnummer: String) = Kandidatliste(
            uuid = UUID.randomUUID(),
            stillingId = stillingId,
            tittel = tittel,
            status = Status.ÅPEN,
            slettet = false,
            virksomhetsnummer = virksomhetsnummer,
            sistEndret = ZonedDateTime.now(),
            opprettet = ZonedDateTime.now()
        )
    }

    enum class Status {
        ÅPEN, LUKKET
    }
}

data class KandidatlisteMedAntallKandidater(
    val kandidatliste: Kandidatliste, val antallKandidater: Int,
)

data class Kandidat(
    @JsonIgnore
    val id: BigInteger? = null,
    val uuid: UUID,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val aktørId: String,
    @JsonIgnore
    val kandidatlisteId: BigInteger,
    val arbeidsgiversVurdering: ArbeidsgiversVurdering,
    val sistEndret: ZonedDateTime = ZonedDateTime.now(),
) {
    companion object {
        fun fraDatabase(rs: ResultSet): Kandidat {
            return Kandidat(
                id = rs.getBigDecimal("id").toBigInteger(),
                uuid = rs.getObject("uuid") as UUID,
                aktørId = rs.getString("aktør_id"),
                kandidatlisteId = rs.getBigDecimal("kandidatliste_id").toBigInteger(),
                arbeidsgiversVurdering = ArbeidsgiversVurdering.valueOf(rs.getString("arbeidsgivers_vurdering")),
                sistEndret = rs.getTimestamp("sist_endret").toInstant().atZone(ZoneId.of("Europe/Oslo"))
            )
        }
    }

    enum class ArbeidsgiversVurdering {
        AKTUELL, TIL_VURDERING, IKKE_AKTUELL, FÅTT_JOBBEN;

        companion object {
            fun fraString(vurdering: String): ArbeidsgiversVurdering {
                require(vurdering in ArbeidsgiversVurdering.values().map { it.name })
                return ArbeidsgiversVurdering.valueOf(vurdering)
            }
        }
    }
}
