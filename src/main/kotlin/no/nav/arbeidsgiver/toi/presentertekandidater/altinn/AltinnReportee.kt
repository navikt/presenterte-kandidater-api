package no.nav.arbeidsgiver.toi.presentertekandidater.altinn

import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable

data class AltinnReportee(
    @JsonProperty("Name")
    val name: String,
    @JsonProperty("ParentOrganizationNumber")
    val parentOrganizationNumber: String?,
    @JsonProperty("OrganizationNumber")
    val organizationNumber: String,
    @JsonProperty("OrganizationForm")
    val organizationForm: String
) : Serializable
