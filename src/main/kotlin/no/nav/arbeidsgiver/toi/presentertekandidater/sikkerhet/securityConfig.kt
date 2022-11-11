package no.nav.arbeidsgiver.toi.presentertekandidater

import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.Rolle
import no.nav.security.token.support.core.configuration.IssuerProperties
import java.net.URL

fun hentIssuerProperties(envs: Map<String, String>) =
    mapOf(
        Rolle.ARBEIDSGIVER to IssuerProperties(
            URL(envs["TOKEN_X_WELL_KNOWN_URL"]),
            listOf(envs["TOKEN_X_CLIENT_ID"]),
            envs["TOKEN_X_PRIVATE_JWK"]
        )
    )
