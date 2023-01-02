package no.nav.arbeidsgiver.toi.presentertekandidater.navalin

import no.nav.arbeidsgiver.toi.presentertekandidater.log
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import java.time.LocalDateTime

data class CachedHandler(
    val handler: JwtTokenValidationHandler,
    val expires: LocalDateTime,
)

private val cache: HashMap<String, CachedHandler> = HashMap()

fun hentTokenValidationHandler(
    issuerProperties: IssuerProperties,
): JwtTokenValidationHandler {
    val cachedHandler = cache["tokendings"]

    return if (cachedHandler != null && cachedHandler.expires.isAfter(LocalDateTime.now())) {
        cachedHandler.handler
    } else {
        val expires = LocalDateTime.now().plusHours(1)
        log("hentTokenValidationHandler").info("Henter og cacher nye public keys for issuer tokendings til $expires")

        val newHandler = JwtTokenValidationHandler(
            MultiIssuerConfiguration(mapOf(issuerProperties.cookieName to issuerProperties))
        )

        cache["tokendings"] = CachedHandler(newHandler, expires);
        newHandler
    }
}