package no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet

import no.nav.security.token.support.core.configuration.IssuerProperties
import java.net.URL

fun hentIssuerPropertiesForTokenX(envs: Map<String, String>) =
    IssuerProperties(
        URL(envs["TOKEN_X_WELL_KNOWN_URL"]),
        listOf(envs["TOKEN_X_CLIENT_ID"]),
        envs["TOKEN_X_PRIVATE_JWK"]
    )
