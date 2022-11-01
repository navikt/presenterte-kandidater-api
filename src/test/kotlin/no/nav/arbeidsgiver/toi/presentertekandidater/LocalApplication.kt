package no.nav.arbeidsgiver.toi.presentertekandidater

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.security.token.support.core.configuration.IssuerProperties
import java.net.URL

fun main() {
    startLocalApplication()
}

fun startLocalApplication() {
    val issuerProperties = mapOf(
        Rolle.ARBEIDSGIVER to IssuerProperties(
            URL("http://localhost:18301/default/.well-known/openid-configuration"),
            listOf("default"),
            "tokenX"
        ),
    )

    val javalin = opprettJavalinMedTilgangskontroll(issuerProperties)
    val rapid = TestRapid()

    startApp(javalin, rapid) { true }
}
