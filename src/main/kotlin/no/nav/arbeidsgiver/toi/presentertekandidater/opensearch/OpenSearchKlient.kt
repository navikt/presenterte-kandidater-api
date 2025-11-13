package no.nav.arbeidsgiver.toi.presentertekandidater.opensearch

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
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result
import no.nav.arbeidsgiver.toi.presentertekandidater.defaultObjectMapper
import no.nav.arbeidsgiver.toi.presentertekandidater.variable
import java.time.LocalDate
import java.time.Period
import java.time.ZonedDateTime

class OpenSearchKlient(envs: Map<String, String>) {
    private val baseUrl = envs.variable("OPEN_SEARCH_URI") + "/kandidater"
    private val searchUrl = "${baseUrl}/_search"

    private val username = envs.variable("OPEN_SEARCH_USERNAME")
    private val password = envs.variable("OPEN_SEARCH_PASSWORD")

    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

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
        val (request, _, result) = Fuel
            .post(searchUrl)
            .jsonBody(lagBodyForHentingAvCver(aktørIder))
            .authentication()
            .basic(username, password)
            .responseString()

        return when (result) {
            is Result.Success -> mapHentCver(aktørIder, result.value)
            is Result.Failure -> {
                val error = result.error
                val msg =
                    "hentCver fra OpenSearch for ${aktørIder.size} CV-er feilet. response.statusCode=[${error.response.statusCode}], responseMessage=[${error.response.responseMessage}], URL=[${request.url}], aktørID-er=[$aktørIder]"
                throw RuntimeException(msg, result.error)
            }
        }
    }

    fun hentAntallKandidater(): Long {
        val (request, _, result) = Fuel
            .get("$baseUrl/_count")
            .authentication()
            .basic(username, password)
            .responseString()

        return when (result) {
            is Result.Success -> objectMapper.readTree(result.value)["count"].asLong()
            is Result.Failure -> {
                val error = result.error
                val msg =
                    "hentAntallKandidater mot OpenSearch feilet. response.statusCode=[${error.response.statusCode}], responseMessage=[${error.response.responseMessage}], URL=[${request.url}]"
                throw RuntimeException(msg, result.error)
            }
        }
    }

    fun lagBodyForHentingAvCver(aktørIder: List<String>) = """
        {
            "size": 10000,
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
                "sprak",
                "forerkort",
                "fagdokumentasjon",
                "godkjenninger",
                "sertifikatObj",
                "kursObj",
                "annenerfaringObj"
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
    val bosted: String?,
    @JsonAlias("mobiltelefon")
    val mobiltelefonnummer: String?,
    @JsonAlias("telefon")
    val telefonnummer: String?,
    @JsonAlias("epostadresse")
    val epost: String?,
    @JsonAlias("fodselsdato")
    @JsonDeserialize(using = AlderDeserializer::class)
    val alder: Int,
    @JsonAlias("kompetanseObj")
    @JsonDeserialize(using = TilStringlisteDeserializer.KompetanseDeserializer::class)
    val kompetanse: List<String>,
    @JsonAlias("yrkeserfaring")
    val arbeidserfaring: List<Arbeidserfaring>,
    @JsonAlias("beskrivelse")
    val sammendrag: String,
    val utdanning: List<Utdanning>,
    @JsonAlias("sprak")
    val språk: List<Språk>,
    @JsonAlias("forerkort")
    val førerkort: List<Førerkort>,
    @JsonDeserialize(using = TilStringlisteDeserializer.FagdokumentasjonDeserializer::class)
    val fagdokumentasjon: List<String>,
    @JsonDeserialize(using = TilStringlisteDeserializer.GodkjenningerDeserializer::class)
    val godkjenninger: List<String>,
    @JsonAlias("sertifikatObj")
    @JsonDeserialize(using = AndreGodkjenningerDeserializer::class)
    val andreGodkjenninger: List<AnnenGodkjenning>,
    @JsonAlias("kursObj")
    @JsonDeserialize(using = KursDeserializer::class)
    val kurs: List<Kurs>,
    @JsonAlias("annenerfaringObj")
    @JsonDeserialize(using = AndreErfaringerDeserializer::class)
    val andreErfaringer: List<AnnenErfaring>,

    )

data class Arbeidserfaring(
    val fraDato: ZonedDateTime?,
    val tilDato: ZonedDateTime?,
    val arbeidsgiver: String?,
    val sted: String?,
    val stillingstittel: String?,
    val beskrivelse: String?,
)

data class Utdanning(
    @JsonAlias("alternativGrad")
    val utdanningsretning: String?,
    val beskrivelse: String?,
    val utdannelsessted: String?,
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

data class Førerkort(
    @JsonAlias("forerkortKodeKlasse")
    val førerkortKodeKlasse: String,
)

data class AnnenGodkjenning(
    val tittel: String,
    val dato: String?
)

data class Kurs(
    val tittel: String,
    val omfangVerdi: Int?,
    val omfangEnhet: String?,
    val tilDato: ZonedDateTime?
)

data class AnnenErfaring(
    val rolle: String,
    val beskrivelse: String?,
    val fraDato: ZonedDateTime?,
    val tilDato: ZonedDateTime?,
)

private class AlderDeserializer : StdDeserializer<Int>(Int::class.java) {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): Int {
        return Period.between(
            ctxt.readValue(parser, LocalDate::class.java), LocalDate.now()
        ).years
    }
}

private abstract class TilStringlisteDeserializer(val felt: String) : StdDeserializer<List<String>>(List::class.java) {

    class KompetanseDeserializer : TilStringlisteDeserializer("kompKodeNavn")
    class FagdokumentasjonDeserializer : TilStringlisteDeserializer("tittel")
    class GodkjenningerDeserializer : TilStringlisteDeserializer("tittel")

    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): List<String> {
        return ctxt.readValue(parser, JsonNode::class.java).mapNotNull { it[felt]?.textValue() }
    }
}

private class AndreGodkjenningerDeserializer : StdDeserializer<List<AnnenGodkjenning>>(List::class.java) {

    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): List<AnnenGodkjenning> {
        val andreGodkjenninger = ctxt.readValue(parser, JsonNode::class.java)

        return andreGodkjenninger.filter {
            val harAlternativtNavn = erString(it["alternativtNavn"])
            val harSertifikatKodeNavn = erString(it["sertifikatKodeNavn"])

            harAlternativtNavn || harSertifikatKodeNavn
        }.map {
            val alternativtNavn = somNullableString(it["alternativtNavn"])
            val sertifikatKodeNavn = somNullableString(it["sertifikatKodeNavn"])
            val tittel: String =
                alternativtNavn ?: sertifikatKodeNavn ?: throw Exception("Skal ha sjekket at én av dem ikke er null")

            AnnenGodkjenning(
                tittel = tittel,
                dato = somNullableString(it["fraDato"])
            )
        }
    }
}

private class KursDeserializer : StdDeserializer<List<Kurs>>(List::class.java) {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): List<Kurs> {
        return ctxt.readValue(parser, JsonNode::class.java)
            .filter { erString(it["tittel"]) }
            .map { it.tilKlasse(Kurs::class.java) }
    }
}

private class AndreErfaringerDeserializer : StdDeserializer<List<AnnenErfaring>>(List::class.java) {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): List<AnnenErfaring> {
        return ctxt.readValue(parser, JsonNode::class.java)
            .filter { erString(it["rolle"]) }
            .map { it.tilKlasse(AnnenErfaring::class.java) }
    }
}

fun somNullableString(jsonNode: JsonNode?): String? =
    if (erString(jsonNode)) {
        jsonNode?.asText()
    } else {
        null
    }

fun erString(jsonNode: JsonNode?) = jsonNode != null && !jsonNode.isNull && jsonNode.asText().isNotEmpty()

fun <T> JsonNode.tilKlasse(type: Class<T>) =
    defaultObjectMapper.readValue(defaultObjectMapper.writeValueAsString(this), type)
