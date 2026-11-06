package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.arbeidsgiver.toi.presentertekandidater.visningkontaktinfo.VisningKontaktinfoPubliserer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.OffsetDateTime
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
        assertThat(førsteMelding["@event_name"].asString()).isEqualTo("arbeidsgiversKandidatliste.VisningKontaktinfo")
        assertThat(førsteMelding["aktørId"].asString()).isEqualTo("123")
        assertThat(førsteMelding["stillingsId"].asString()).isEqualTo(stillingsId.toString())
        assertThat(ZonedDateTime.parse(førsteMelding["tidspunkt"].asString())).isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS))

        val andreMelding = inspektør.message(1)
        assertThat(andreMelding["@event_name"].asString()).isEqualTo("arbeidsgiversKandidatliste.VisningKontaktinfo")
        assertThat(andreMelding["aktørId"].asString()).isEqualTo("234")
        assertThat(andreMelding["stillingsId"].asString()).isEqualTo(stillingsId.toString())
        assertThat(ZonedDateTime.parse(andreMelding["tidspunkt"].asString())).isCloseTo(ZonedDateTime.now(), within(1, ChronoUnit.SECONDS))
    }

    @Test
    fun `Publiserer visning av kontaktinfo med fast JSON og datoformat`() {
        val stillingsId = UUID.fromString("1827cddd-736c-4926-ad2a-a657a1cefa1d")
        val aktørId = "123"
        visningKontaktinfoRepository.registrerVisning(aktørId, stillingsId)
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "update visning_kontaktinfo set tidspunkt = ? where aktør_id = ? and stilling_id = ?"
            ).use { statement ->
                statement.setObject(1, OffsetDateTime.parse("2024-07-15T12:30:00+02:00"))
                statement.setString(2, aktørId)
                statement.setObject(3, stillingsId)
                statement.executeUpdate()
            }
        }

        visningKontaktinfoPubliserer.publiser()

        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(1)
        assertThat(inspektør.key(0)).isNull()
        val actual = json(inspektør.message(0).toString())
        val expected = json(
            """
            {
              "stillingsId": "$stillingsId",
              "tidspunkt": "2024-07-15T12:30:00+02:00",
              "aktørId": "$aktørId",
              "@event_name": "arbeidsgiversKandidatliste.VisningKontaktinfo"
            }
            """
        )
        assertThat(actual).isEqualTo(expected)
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
