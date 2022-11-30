package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Test
import java.io.File

class MidlertidigTest {

    @Test
    fun hentUtData() {
        val json = defaultObjectMapper.readTree(File("/Users/mads/IdeaProjects/presenterte-kandidater-api/src/test/resources/bedrifterMidl.json").readText(Charsets.UTF_8))
        println("Struktur på et dokument")
        val dokumentListe = json["dokumentListe"]

        val virksomheter = dokumentListe.map { lagVirksomhet(it) }
        printBulkInsert(virksomheter)
    //        println(defaultObjectMapper.writeValueAsString(virksomheter))
    }

    private fun lagVirksomhet(node: JsonNode): Virksomhet {
        val forretningsAdresse = node["forretningsadresse"]
        val location = lagLocation(forretningsAdresse)
        val virksomhet = Virksomhet(
            orgnr = node["organisasjonsnummer"].asText(),
            name = node["navn"].asText(),
            location = location,
            hovedenhet = node["underenhet"]["hovedenhet"].asText(),
            organisasjonsform = node["organisasjonsform"]["kode"].asText(),
            antallAnsatte = node["antallAnsatte"].asInt()
        )

        return virksomhet
    }

    data class Virksomhet(
        val orgnr: String?,
        val name: String,
        val location: Location?,
        @JsonIgnore
        val hovedenhet: String,
        @JsonIgnore
        val organisasjonsform: String,
        @JsonIgnore
        val antallAnsatte: Int
    )

    data class Location(
        val address: String,
        val postalCode: String,
        val city: String?,
        val municipal: String?,
        val country: String?
    )

    private fun printBulkInsert(virksomheter: List<Virksomhet>) {
        val insert = "INSERT INTO company (name, orgnr, status, parentorgnr, publicname, deactivated, orgform, employees) VALUES\n"
        val values = virksomheter.map { "(${lagDatabaserad(it)})" }.joinToString(",\n")
        print("$insert$values;")
    }

    private fun lagDatabaserad(virksomhet: Virksomhet) =
        "'${virksomhet.name}', '${virksomhet.orgnr}', 'ACTIVE', '${virksomhet.hovedenhet}', '${virksomhet.name}', false, '${virksomhet.organisasjonsform}', ${virksomhet.antallAnsatte}"


    private fun lagLocation(forretningsAdresse: JsonNode): Location {
        val adress = forretningsAdresse["adresse"][0]?.asText() ?: ""
        val postalCode = forretningsAdresse["postnummer"]?.asText() ?: ""
        val city = forretningsAdresse["poststed"]?.asText()
        val municipal = forretningsAdresse["kommune"]?.asText()
        val country = forretningsAdresse["land"]?.asText()

        return Location(adress, postalCode, city, municipal, country)
    }
}