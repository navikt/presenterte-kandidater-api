package no.nav.arbeidsgiver.toi.presentertekandidater

import java.time.ZonedDateTime
import java.util.UUID

data class Kandidathendelse(
    val type: Type,
    val aktørId: String,
    val organisasjonsnummer: String,
    val tidspunkt: ZonedDateTime,
    val stillingsId: UUID,
    val utførtAvNavIdent: String? = null,
    val utførtAvNavKontorKode: String? = null,
)

enum class Type(private val eventNamePostfix: String) {
    CV_DELT_VIA_REKRUTTERINGSBISTAND("kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand"),
    ANNULLERT("kandidat.annullert"),
    SLETTET_FRA_ARBEIDSGIVERS_KANDIDATLISTE("kandidat.slettet-fra-arbeidsgivers-kandidatliste"),
    KANDIDATLISTE_LUKKET_NOEN_ANDRE_FIKK_JOBBEN("kandidat.kandidatliste-lukket-noen-andre-fikk-jobben"),
    KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN("kandidat.kandidatliste-lukket-ingen-fikk-jobben")
}
