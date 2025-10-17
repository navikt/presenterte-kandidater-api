package no.nav.arbeidsgiver.toi.presentertekandidater.altinn

import java.io.Serializable

data class AltinnReportee(
    val name: String,
    val parentOrganizationNumber: String?,
    val organizationNumber: String,
    val organizationForm: String
) : Serializable
