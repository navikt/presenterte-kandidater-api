package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.arbeidsgiver.toi.presentertekandidater.visningkontaktinfo.VisningKontaktinfoPubliserer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VisningKontaktinfoPublisererTest {

    private val visningKontaktinfoRepository = visningKontaktinfoRepositoryMedLokalPostgres()
    private val rapid = TestRapid()
    private val visningKontaktinfoPubliserer = VisningKontaktinfoPubliserer(rapid, visningKontaktinfoRepository)

    @BeforeAll
    fun init() {
        startLocalApplication()
    }

    @BeforeEach
    fun setUp() {
        rapid.reset()
        slettAllDataIDatabase()
    }

    @Test
    fun `Skal publisere visningAvKontaktinfo-meldinger for rader som ikke allerede er publisert`() {
        val stillingsId = UUID.randomUUID()
        visningKontaktinfoRepository.registrerVisning("123", stillingsId)
        visningKontaktinfoRepository.registrerVisning("234", stillingsId)

        visningKontaktinfoPubliserer.publiser()

        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(2)
        val førsteMelding = inspektør.message(0)
        assertThat(førsteMelding["@event_name"].asText()).isEqualTo("arbeidsgiversKandidatliste.VisningKontaktinfo")
        assertThat(førsteMelding["aktørId"].asText()).isEqualTo("123")
        assertThat(førsteMelding["stillingsId"].asText()).isEqualTo(stillingsId.toString())
        assertThat(ZonedDateTime.parse(førsteMelding["tidspunkt"].asText())).isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS))

        val andreMelding = inspektør.message(1)
        assertThat(andreMelding["@event_name"].asText()).isEqualTo("arbeidsgiversKandidatliste.VisningKontaktinfo")
        assertThat(andreMelding["aktørId"].asText()).isEqualTo("234")
        assertThat(andreMelding["stillingsId"].asText()).isEqualTo(stillingsId.toString())
        assertThat(ZonedDateTime.parse(andreMelding["tidspunkt"].asText())).isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS))
    }

    @Test
    fun `Etter publisering av visningAvKontaktinfo-meldinger skal radene bli markert som publisert`() {
        val stillingsId = UUID.randomUUID()
        visningKontaktinfoRepository.registrerVisning("123", stillingsId)
        visningKontaktinfoRepository.registrerVisning("234", stillingsId)

        visningKontaktinfoPubliserer.publiser()

        val registrerteVisninger = visningKontaktinfoRepository.hentAlleRegistrerteVisninger()
        val statusForAlleVisninger = registrerteVisninger.map { it.publisertMelding }
        assertTrue(statusForAlleVisninger.all { it })
    }

    @Test
    fun `Skal ikke publisere visningAvKontaktinfo-meldinger som er markert som publisert`() {
        visningKontaktinfoRepository.registrerVisning("123", UUID.randomUUID())
        val alleRegistrerteVisninger = visningKontaktinfoRepository.hentAlleRegistrerteVisninger()
        assertThat(alleRegistrerteVisninger.size).isEqualTo(1)
        val registrertVisning = alleRegistrerteVisninger.first()

        visningKontaktinfoRepository.markerSomPublisert(registrertVisning)
        visningKontaktinfoPubliserer.publiser()

        assertThat(rapid.inspektør.size).isEqualTo(0)
    }
}