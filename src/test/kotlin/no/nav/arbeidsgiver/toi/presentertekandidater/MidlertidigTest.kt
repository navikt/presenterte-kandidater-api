package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Test
import java.io.File

class MidlertidigTest {

    @Test
    fun hentUtData() {
        // virksomhetsnummer, adresse, navn
        val json = defaultObjectMapper.readTree(File("/Users/mads/IdeaProjects/presenterte-kandidater-api/src/test/resources/bedrifterMidl.json").readText(Charsets.UTF_8))
        println("Struktur på et dokument")
        val dokumentListe = json["dokumentListe"]

        val virksomheter = dokumentListe.map { lagVirksomhet(it) }
        println(defaultObjectMapper.writeValueAsString(virksomheter))
    }

    private fun lagVirksomhet(node: JsonNode): Virksomhet {
        val orgnr = node["organisasjonsnummer"].asText()
        val name = node["navn"].asText()

        val forretningsAdresse = node["forretningsadresse"]
        val location = lagLocation(forretningsAdresse)
        val virksomhet = Virksomhet(
            orgnr = node["organisasjonsnummer"].asText(),
            name = node["navn"].asText(),
            location = location
        )

        return virksomhet
    }

    data class Virksomhet(
        val orgnr: String?,
        val name: String,
        val location: Location?
    )

    data class Location(
        val address: String,
        val postalCode: String,
        val city: String?,
        val municipal: String?,
        val country: String?
    )

    private fun lagLocation(forretningsAdresse: JsonNode): Location {
        val adress = forretningsAdresse["adresse"][0]?.asText() ?: ""
        val postalCode = forretningsAdresse["postnummer"]?.asText() ?: ""
        val city = forretningsAdresse["poststed"]?.asText()
        val municipal = forretningsAdresse["kommune"]?.asText()
        val country = forretningsAdresse["land"]?.asText()

        return Location(adress, postalCode, city, municipal, country)
    }
}