package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel

class OpenSearchKlient(private val envs: Map<String, String>) {

    fun hentKandiat(aktørid: String): KandidatFraOpenSearch? {
        val url = envs["OPENSEARCH_URL"] +
        "/veilederkandidat_current/_search?q=aktorId:$aktørid"

        val (_, response) = Fuel.get(url).response()
        return if (response.statusCode == 200) {
            log.info("Hentkandidat fra openserch ok")
            val body = response.body().asString("UTF-8")
            mapHentKandidat(body)

        } else if (response.statusCode == 404) {
            log.info("Hentkandidat fra openserch fant ikke kandidat")
            null
        } else {
            log.error("Hentkandidat fra openserch feilet: ${response.statusCode} ${response.responseMessage}")
            throw RuntimeException("Kall mot elsaticsearch feilet for aktørid $aktørid")
        }
    }

    private fun mapHentKandidat(body: String): KandidatFraOpenSearch {
        val kandidat: KandidatFraOpenSearch = jacksonObjectMapper().readValue(body,KandidatFraOpenSearch::class.java)
        return kandidat
    }
}

data class KandidatFraOpenSearch(
    @JsonAlias("aktorId")
    val aktørId: String,
    val fornavn: String,
    val etternavn: String)

