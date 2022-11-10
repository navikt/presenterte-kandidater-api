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

enum class Type() {
    REGISTRER_CV_DELT,
    CV_DELT_VIA_REKRUTTERINGSBISTAND,
    REGISTRER_FÅTT_JOBBEN,
    FJERN_REGISTRERING_AV_CV_DELT,
    FJERN_REGISTRERING_FÅTT_JOBBEN,
    ANNULLERT,
    SLETTET_FRA_ARBEIDSGIVERS_KANDIDATLISTE,
    KANDIDATLISTE_LUKKET_NOEN_ANDRE_FIKK_JOBBEN,
    KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN
}
