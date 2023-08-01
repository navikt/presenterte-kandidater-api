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

private val cache: HashMap<NavalinAccessManager.TokenUtsteder, CachedHandler> = HashMap()

fun hentTokenValidationHandler(
    issuerProperties: IssuerProperties,
    tokenUtsteder: NavalinAccessManager.TokenUtsteder
): JwtTokenValidationHandler {
    val cachedHandler = cache[tokenUtsteder]

    return if (cachedHandler != null && cachedHandler.expires.isAfter(LocalDateTime.now())) {
        cachedHandler.handler
    } else {
        val expires = LocalDateTime.now().plusHours(1)
        log("hentTokenValidationHandler").info("Henter og cacher nye public keys for issuer tokendings til $expires")

        val newHandler = JwtTokenValidationHandler(
            MultiIssuerConfiguration(mapOf(issuerProperties.cookieName to issuerProperties))
        )

        cache[tokenUtsteder] = CachedHandler(newHandler, expires);
        newHandler
    }
}