package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import javax.management.monitor.StringMonitor

class OpenSearchKlient(private val envs: Map<String, String>) {

    val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun hentKandiat(aktørid: String): KandidatFraOpenSearch? {
        val url = envs["OPENSEARCH_URL"] +
        "/veilederkandidat_current/_search?q=aktorId:$aktørid"

        val (_, response, result) = Fuel
            .get(url)
            .authentication()
            .basic(envs["OPENSEARCH_USERNAME"]!!, envs["OPENSEARCH_PASSWORD"]!!)
            .responseString()

        return if (response.statusCode == 200) {
            log.info("Hentkandidat fra openserch ok")
            val body = result.get()
            mapHentÉnKandidat(body)

        } else if (response.statusCode == 404) {
            log.info("Hentkandidat fra openserch fant ikke kandidat")
            null
        } else {
            log.error("Hentkandidat fra openserch feilet: ${response.statusCode} ${response.responseMessage}")
            throw RuntimeException("Kall mot elsaticsearch feilet for aktørid $aktørid")
        }
    }

    private fun mapHentÉnKandidat(body: String): KandidatFraOpenSearch? {
        val responsJsonNode = objectMapper.readTree(body)
        val hits = responsJsonNode["hits"]["hits"]
        val harTreff = hits.toList().isNotEmpty()

        return if (harTreff) {
            val kandidatJson = objectMapper.writeValueAsString(hits.first()["_source"])
            return objectMapper.readValue(kandidatJson,KandidatFraOpenSearch::class.java)
        } else {
            null
        }
    }
}

data class KandidatFraOpenSearch(
    @JsonAlias("aktorId")
    val aktørId: String,
    val fornavn: String,
    val etternavn: String,
    @JsonAlias("poststed")
    val bosted: String,
    @JsonAlias("mobiltelefon")
    val mobiltelefonnummer: String,
    @JsonAlias("epostadresse")
    val epost: String
)

