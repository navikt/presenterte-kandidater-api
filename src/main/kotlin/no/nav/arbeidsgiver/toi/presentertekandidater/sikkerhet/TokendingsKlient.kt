package no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet

import com.fasterxml.jackson.annotation.JsonAlias
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import no.nav.arbeidsgiver.toi.presentertekandidater.variable

class TokendingsKlient(envs: Map<String, String>) {
    private val cache = hashMapOf<String, ExchangeToken>()
    private val url = envs.variable("TOKEN_X_WELL_KNOWN_URL")
    private val scope = envs.variable("ALTINN_PROXY_AUDIENCE")

    init {
        TODO("HENT SIGNERT JWK")
    }

    fun veksleInnToken(token: String): String {
        // TODO: Cache
        val formData = listOf(
            "grant_type" to "urn:ietf:params:oauth:grant-type:token-exchange",
            "grant_type" to "urn:ietf:params:oauth:grant-type:token-exchange",
            "client_assertion" to "TODO",
            "client_assertion_type" to "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
            "subject_token_type" to "urn:ietf:params:oauth:token-type:jwt",
            "audience" to scope,
            "subject_token" to token,
        )

        val (_, _, result) = Fuel.post(url, formData).responseObject<ExchangeToken>()

        when (result) {
            is Result.Failure -> {
                throw RuntimeException("Kunne ikke veksle inn token hos TokenX")
            }
            is Result.Success -> {
                return result.get().accessToken
            }
        }
    }

    data class ExchangeToken(
        @JsonAlias("access_token")
        val accessToken: String,
        @JsonAlias("expires_in")
        val expiresIn: Int
    )
}
