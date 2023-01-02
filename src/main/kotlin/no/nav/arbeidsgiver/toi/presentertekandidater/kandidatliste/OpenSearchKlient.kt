package no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result
import no.nav.arbeidsgiver.toi.presentertekandidater.log
import no.nav.arbeidsgiver.toi.presentertekandidater.variable
import java.time.Period
import java.time.ZonedDateTime

class OpenSearchKlient(envs: Map<String, String>) {
    private val url = envs.variable("OPEN_SEARCH_URI") + "/veilederkandidat_current/_search"
    private val username = envs.variable("OPEN_SEARCH_USERNAME")
    private val password = envs.variable("OPEN_SEARCH_PASSWORD")

    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private fun post(body: String): Pair<Response, Result<String, FuelError>> {
        val (_, response, result) = Fuel
            .post(url)
            .jsonBody(body)
            .authentication()
            .basic(username, password)
            .responseString()

        return Pair(response, result)
    }

    private fun mapHentCver(aktørider: List<String>, body: String): Map<String, Cv?> {
        val responsJsonNode = objectMapper.readTree(body)

        val hits = responsJsonNode["hits"]["hits"]
        val cver = hits
            .map { it["_source"] }
            .map { json ->
                val cvJson = objectMapper.writeValueAsString(json)
                objectMapper.readValue(cvJson, Cv::class.java)
            }.associateBy { it.aktørId }

        return aktørider.associateWith { cver[it] }
    }

    fun hentCver(aktørIder: List<String>): Map<String, Cv?> {
        val (respons, resultat) = post(lagBodyForHentingAvCver(aktørIder))

        return when (respons.statusCode) {
            200 -> {
                val data = resultat.get()
                mapHentCver(aktørIder, data)
            }

            else -> {
                log.error("hentCver fra OpenSearch feilet: ${respons.statusCode} ${respons.responseMessage}")
                throw RuntimeException("Kall mot elsaticsearch feilet for aktørIder $aktørIder")
            }
        }

    }

    fun hentAktørid(kandidatnr: String, stillingId: String): String? {
        val (respons, resultat) = post(lagBodyForHentingAvAktørId(kandidatnr))

        return when (respons.statusCode) {
            200 -> {
                log.info("hentAktørId fra OpenSearch ok")

                val data = resultat.get()
                val responsJsonNode = objectMapper.readTree(data)

                val hits = responsJsonNode["hits"]["hits"]

                if (hits == null || hits.size() == 0) {
                    log.info("Fant ikke $kandidatnr i stilling $stillingId opensearch");
                    return null
                }

                hits
                    .map { it["fields"]["aktorId"] }
                    .first().first().asText()
            }

            404 -> {
                null
            }

            else -> {
                log.error("hentkandidatNr for $kandidatnr mot OpenSearch feilet: ${respons.statusCode} ${respons.responseMessage}")
                throw RuntimeException("Kall mot openSearch feilet for kandidatNr $kandidatnr")
            }
        }
    }

    fun lagBodyForHentingAvAktørId(kandidatnr: String) = """
            {
                "query": {
                    "term": {
                        "kandidatnr": {
                            "value": "$kandidatnr"
                        }
                    }
                },
                 "fields": [
                     "aktorId"
                 ],
              "_source": false
            }
    """.trimIndent()

    fun lagBodyForHentingAvCver(aktørIder: List<String>) = """
        {
            "query": {
                "terms": {
                    "aktorId": [
                        ${aktørIder.joinToString(",") { "\"$it\"" }}
                    ]
                }
            },
            "_source": [
                "aktorId",
                "poststed",
                "mobiltelefon",
                "telefon",
                "epostadresse",
                "fornavn",
                "etternavn",
                "fodselsdato",
                "alder",
                "kompetanseObj",
                "yrkeserfaring",
                "beskrivelse",
                "utdanning",
                "sprak"
            ]
        }
        """

}

data class Cv(
    @JsonAlias("aktorId") @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val aktørId: String,
    val fornavn: String,
    val etternavn: String,
    @JsonAlias("poststed")
    val bosted: String,
    @JsonAlias("mobiltelefon")
    val mobiltelefonnummer: String?,
    @JsonAlias("telefon")
    val telefonnummer: String?,
    @JsonAlias("epostadresse")
    val epost: String?,
    @JsonAlias("fodselsdato") @JsonDeserialize(using = AlderDeserializer::class)
    val alder: Int,
    @JsonAlias("kompetanseObj") @JsonDeserialize(using = TilStringlisteDeserializer.KompetanseDeserializer::class)
    val kompetanse: List<String>,
    @JsonAlias("yrkeserfaring")
    val arbeidserfaring: List<Arbeidserfaring>,
    @JsonAlias("beskrivelse")
    val sammendrag: String,
    val utdanning: List<Utdanning>,
    @JsonAlias("sprak")
    val språk: List<Språk>,
)

data class Arbeidserfaring(
    val fraDato: ZonedDateTime,
    val tilDato: ZonedDateTime?,
    val arbeidsgiver: String,
    val sted: String,
    val stillingstittel: String,
    val beskrivelse: String,
)

data class Utdanning(
    @JsonAlias("alternativGrad")
    val utdanningsretning: String,
    val beskrivelse: String,
    val utdannelsessted: String,
    @JsonAlias("fraDato")
    val fra: ZonedDateTime,
    @JsonAlias("tilDato")
    val til: ZonedDateTime?,
)

data class Språk(
    @JsonAlias("sprakKodeTekst")
    val navn: String,
    @JsonAlias("ferdighetMuntlig")
    val muntlig: String,
    @JsonAlias("ferdighetSkriftlig")
    val skriftlig: String,
)


private class AlderDeserializer : StdDeserializer<Int>(Int::class.java) {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): Int {
        return Period.between(
            ctxt.readValue(parser, ZonedDateTime::class.java).toLocalDate(), ZonedDateTime.now().toLocalDate()
        ).years
    }
}

private abstract class TilStringlisteDeserializer(val felt: String) : StdDeserializer<List<String>>(List::class.java) {
    class KompetanseDeserializer : TilStringlisteDeserializer("kompKodeNavn")

    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): List<String> {
        return ctxt.readValue(parser, JsonNode::class.java).map { it[felt].textValue() }
    }
}
