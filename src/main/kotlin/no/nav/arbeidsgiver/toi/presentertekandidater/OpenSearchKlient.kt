package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.result.Result
import java.time.Period
import java.time.ZonedDateTime


class OpenSearchKlient(private val envs: Map<String, String>) {

    val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun hentKandiat(aktørid: String): OpensearchData.Kandidat? {
        val (response, result) = hentKandidat(aktørid)

        return when (response.statusCode) {
            200 -> {
                log.info("Hentkandidat fra openserch ok")
                val body = result.get()
                mapHentÉnKandidat(body)

            }

            404 -> {
                log.info("Hentkandidat fra openserch fant ikke kandidat")
                null
            }

            else -> {
                log.error("Hentkandidat fra openserch feilet: ${response.statusCode} ${response.responseMessage}")
                throw RuntimeException("Kall mot elsaticsearch feilet for aktørid $aktørid")
            }
        }
    }

    private fun mapHentÉnKandidat(body: String): OpensearchData.Kandidat? {
        val responsJsonNode = objectMapper.readTree(body)
        val hits = responsJsonNode["hits"]["hits"]
        val harTreff = hits.toList().isNotEmpty()

        return if (harTreff) {
            val kandidatJson = objectMapper.writeValueAsString(hits.first()["_source"])
            return objectMapper.readValue(kandidatJson, OpensearchData.Kandidat::class.java)
        } else {
            null
        }
    }

    private fun mapHentKandidatsammendrag(body: String): OpensearchData.Kandidatsammendrag? {
        val responsJsonNode = objectMapper.readTree(body)
        val hits = responsJsonNode["hits"]["hits"]
        val harTreff = hits.toList().isNotEmpty()

        return if (harTreff) {
            val kandidatJson = objectMapper.writeValueAsString(hits.first()["_source"])
            return objectMapper.readValue(kandidatJson, OpensearchData.Kandidatsammendrag::class.java)
        } else {
            null
        }
    }

    fun hentKandidatsammendrag(aktørid: String): OpensearchData.Kandidatsammendrag? {
        val (response, result) = hentKandidat(aktørid)

        return when (response.statusCode) {
            200 -> {
                log.info("hentKandidatsammendrag fra openserch ok")
                val body = result.get()
                mapHentKandidatsammendrag(body)

            }

            404 -> {
                log.info("hentKandidatsammendrag fra openserch fant ikke kandidat")
                null
            }

            else -> {
                log.error("hentKandidatsammendrag fra openserch feilet: ${response.statusCode} ${response.responseMessage}")
                throw RuntimeException("Kall mot elsaticsearch feilet for aktørid $aktørid")
            }
        }
    }

    private fun hentKandidat(aktørid: String): Pair<Response, Result<String, FuelError>> {
        val url = envs["OPENSEARCH_URL"] +
                "/veilederkandidat_current/_search?q=aktorId:$aktørid"

        val (_, response, result) = Fuel
            .get(url)
            .authentication()
            .basic(envs["OPENSEARCH_USERNAME"]!!, envs["OPENSEARCH_PASSWORD"]!!)
            .responseString()
        return Pair(response, result)
    }

    //TODO: Skriv om til et opensearch kall for å hente alle cver/kandidater samtidig
    fun hentKandidater(aktørIder: List<String>): Map<String, OpensearchData.Kandidatsammendrag?> =
        aktørIder.map {
            val kandidat = hentKandidatsammendrag(it)
            it to kandidat
        }.toMap()
}

class OpensearchData {

    data class Kandidatsammendrag(
        val fornavn: String,
        val etternavn: String,
        @JsonAlias("kompetanseObj")
        @JsonDeserialize(using = TilStringlisteDeserializer.KompetanseDeserializer::class)
        val kompetanse: List<String>,
        @JsonAlias("yrkeserfaring")
        @JsonDeserialize(using = TilStringlisteDeserializer.ArbeidserfaringDeserializer::class)
        val arbeidserfaring: List<String>,
        @JsonAlias("yrkeJobbonskerObj")
        @JsonDeserialize(using = TilStringlisteDeserializer.ØnsketYrkeDeserializer::class)
        val ønsketYrke: List<String>
    )

    data class Kandidat(
        @JsonAlias("aktorId")
        val aktørId: String,
        val fornavn: String,
        val etternavn: String,
        @JsonAlias("poststed")
        val bosted: String,
        @JsonAlias("mobiltelefon")
        val mobiltelefonnummer: String,
        @JsonAlias("epostadresse")
        val epost: String,
        @JsonAlias("fodselsdato")
        @JsonDeserialize(using = AlderDeserializer::class)
        val alder: Int,
        @JsonAlias("kompetanseObj")
        @JsonDeserialize(using = TilStringlisteDeserializer.KompetanseDeserializer::class)
        val kompetanse: List<String>,
        @JsonAlias("yrkeserfaring")
        val arbeidserfaring: List<Arbeidserfaring>,
        @JsonAlias("yrkeJobbonskerObj")
        @JsonDeserialize(using = TilStringlisteDeserializer.ØnsketYrkeDeserializer::class)
        val ønsketYrke: List<String>,
        @JsonAlias("beskrivelse")
        val sammendrag: String,
        val utdanning: List<Utdanning>,
        @JsonAlias("sprak")
        val språk: List<Språk>
    )

    data class Arbeidserfaring(
        val fraDato: ZonedDateTime,
        val tilDato: ZonedDateTime,
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
        val til: ZonedDateTime
    )

    data class Språk(
        @JsonAlias("sprakKodeTekst")
        val navn: String,
        @JsonAlias("ferdighetMuntlig")
        val muntlig: String,
        @JsonAlias("ferdighetSkriftlig")
        val skriftlig: String
    )
}

private class AlderDeserializer : StdDeserializer<Int>(Int::class.java) {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): Int {
        return Period.between(
            ctxt.readValue(parser, ZonedDateTime::class.java).toLocalDate(),
            ZonedDateTime.now().toLocalDate()
        ).years
    }
}

private abstract class TilStringlisteDeserializer(val felt: String) : StdDeserializer<List<String>>(List::class.java) {
    class KompetanseDeserializer : TilStringlisteDeserializer("kompKodeNavn")
    class ØnsketYrkeDeserializer : TilStringlisteDeserializer("styrkBeskrivelse")

    class ArbeidserfaringDeserializer : TilStringlisteDeserializer("stillingstittel")

    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): List<String> {
        return ctxt.readValue(parser, JsonNode::class.java).map { it[felt].textValue() }
    }
}


