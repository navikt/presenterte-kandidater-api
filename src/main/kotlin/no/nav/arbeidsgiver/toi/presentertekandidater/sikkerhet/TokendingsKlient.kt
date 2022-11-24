package no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import no.nav.arbeidsgiver.toi.presentertekandidater.log
import no.nav.arbeidsgiver.toi.presentertekandidater.variable
import java.util.*


class TokendingsKlient(envs: Map<String, String>) {
    private val cache = hashMapOf<String, ExchangeToken>()

    private val tokenDingsExchangeUrl = envs.variable("TOKEN_X_TOKEN_ENDPOINT")
    private val privateJwk = envs.variable("TOKEN_X_PRIVATE_JWK")
    private val clientId = envs.variable("TOKEN_X_CLIENT_ID")
    private val issuer = envs.variable("TOKEN_X_ISSUER")

    fun veksleInnToken(accessToken: String, scope: String): String {
        // TODO: Cache
        val formData = listOf(
            "grant_type" to "urn:ietf:params:oauth:grant-type:token-exchange",
            "client_assertion" to getClientAssertion(TokenXProperties(clientId, issuer, privateJwk, tokenDingsExchangeUrl)),
            "client_assertion_type" to "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
            "subject_token_type" to "urn:ietf:params:oauth:token-type:jwt",
            "audience" to scope,
            "subject_token" to accessToken,
        )

        val (_, _, result) = Fuel.post(tokenDingsExchangeUrl, formData).responseObject<ExchangeToken>()

        when (result) {
            is Result.Failure -> {
                throw RuntimeException("Kunne ikke veksle inn token hos TokenX:", result.getException())
            }
            is Result.Success -> {
                log.info("Vekslet inn token hos TokenX")
                return result.get().accessToken
            }
        }
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
        val tokenEndpoint: String
    ) {
        fun parseJwk() = RSAKey.parse(privateJwk)
        fun getJwsSigner() = RSASSASigner(parseJwk())
    }

    data class ExchangeToken(
        @JsonAlias("access_token")
        val accessToken: String,
        @JsonAlias("expires_in")
        val expiresIn: Int
    )
}
