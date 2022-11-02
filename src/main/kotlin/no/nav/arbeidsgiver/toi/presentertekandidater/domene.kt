package no.nav.arbeidsgiver.toi.presentertekandidater

import java.math.BigInteger
import java.time.LocalDate
import java.util.UUID

data class Kandidatliste(
    val id: BigInteger,
    val stillingId: UUID,
    val tittel: String,
    val status: String,
    val slettet: Boolean = false,
    val virksomhetsnummer: String,
)

data class Kandidat(
    val id: BigInteger,
    val aktørId: String,
    val kandidatlisteId: BigInteger,
    val hendelsestidspunkt: LocalDate,
    val hendelsestype: String,
    val arbeidsgiversStatus: String,
)
