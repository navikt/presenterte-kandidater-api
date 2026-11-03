package no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import no.nav.arbeidsgiver.toi.presentertekandidater.log
import no.nav.arbeidsgiver.toi.presentertekandidater.variable
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.util.*


class TokendingsKlient(envs: Map<String, String>) {
    private val tokenDingsExchangeUrl = envs.variable("TOKEN_X_TOKEN_ENDPOINT")
    private val privateJwk = envs.variable("TOKEN_X_PRIVATE_JWK")
    private val clientId = envs.variable("TOKEN_X_CLIENT_ID")
    private val issuer = envs.variable("TOKEN_X_ISSUER")
    private val timeout = Duration.ofSeconds(15)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
    private val objectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun veksleInnToken(accessToken: String, scope: String): String {
        val formData = listOf(
            "grant_type" to "urn:ietf:params:oauth:grant-type:token-exchange",
            "client_assertion" to getClientAssertion(
                TokenXProperties(
                    clientId,
                    issuer,
                    privateJwk,
                    tokenDingsExchangeUrl
                )
            ),
            "client_assertion_type" to "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
            "subject_token_type" to "urn:ietf:params:oauth:token-type:jwt",
            "audience" to scope,
            "subject_token" to accessToken,
        )

        val body = formData.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, UTF_8)}=${URLEncoder.encode(requireNotNull(value), UTF_8)}"
        }
        val request = HttpRequest.newBuilder(URI(tokenDingsExchangeUrl))
            .timeout(timeout)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body, UTF_8))
            .build()
        val feilmelding = "Kunne ikke veksle inn token hos TokenX"
        val response = try {
            httpClient.send(request, BodyHandlers.ofString(UTF_8))
        } catch (e: InterruptedException) {
            // InterruptedException nullstiller interrupt-status. Gjenopprett avbruddssignalet
            // slik at annen kode kan oppdage det selv om vi kaster RuntimeException.
            Thread.currentThread().interrupt()
            throw RuntimeException(feilmelding, e)
        } catch (e: IOException) {
            throw RuntimeException(feilmelding, e)
        }

        if (response.statusCode() !in 200..299) {
            throw RuntimeException(feilmelding)
        }

        val exchangeToken = try {
            objectMapper.readValue<ExchangeToken>(response.body())
        } catch (e: IOException) {
            throw RuntimeException(feilmelding, e)
        }
        log.info("Vekslet inn token hos TokenX")
        return exchangeToken.accessToken
    }

    fun getClientAssertion(properties: TokenXProperties): String? {
        val claimsSet: JWTClaimsSet = JWTClaimsSet.Builder()
            .subject(properties.clientId)
            .issuer(properties.clientId)
            .audience(properties.tokenEndpoint)
            .issueTime(Date())
            .notBeforeTime(Date())
            .expirationTime(Date(Date().getTime() + 120 * 1000))
            .jwtID(UUID.randomUUID().toString())
            .build()
        val signedJWT = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(properties.parseJwk().keyID)
                .build(),
            claimsSet
        )
        try {
            signedJWT.sign(properties.getJwsSigner())
        } catch (e: JOSEException) {
            throw RuntimeException(e)
        }
        return signedJWT.serialize()
    }


    data class TokenXProperties(
        val clientId: String,
        val issuer: String,
        val privateJwk: String,
        val tokenEndpoint: String,
    ) {
        fun parseJwk() = RSAKey.parse(privateJwk)
        fun getJwsSigner() = RSASSASigner(parseJwk())
    }

    data class ExchangeToken(
        @JsonAlias("access_token")
        val accessToken: String,
        @JsonAlias("expires_in")
        val expiresIn: Int,
    )
}
