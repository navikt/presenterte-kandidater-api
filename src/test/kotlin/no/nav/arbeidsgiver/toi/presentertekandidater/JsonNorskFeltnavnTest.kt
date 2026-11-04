package no.nav.arbeidsgiver.toi.presentertekandidater

import tools.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private data class JsonFeltMedNorskStartbokstav(val ønske: String)

class JsonNorskFeltnavnTest {
    @Test
    fun `Mapperen serialiserer og deserialiserer feltnavn som begynner med norsk bokstav`() {
        val original = JsonFeltMedNorskStartbokstav(ønske = "fast verdi")

        val json = defaultObjectMapper.writeValueAsString(original)

        assertThat(json).isEqualTo("""{"ønske":"fast verdi"}""")
        assertThat(defaultObjectMapper.readValue<JsonFeltMedNorskStartbokstav>(json)).isEqualTo(original)
    }
}
