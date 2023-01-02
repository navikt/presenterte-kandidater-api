package no.nav.arbeidsgiver.toi.presentertekandidater

import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat.ArbeidsgiversVurdering.*
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.statistikk.StatistikkRepository
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigInteger
import java.time.ZonedDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class StatistikkRepositoryTest {
    private val kandidatlisteRepository = kandidatlisteRepositoryMedLokalPostgres()
    private val statistikkRepository = StatistikkRepository(dataSource)

    @BeforeAll
    fun beforeAll() {
        kjørFlywayMigreringer(dataSource)
    }

    @AfterEach
    fun afterEach() {
        slettAltIDatabase()
    }

    @Test
    fun `Tell antall kandidatlister`() {
        val kandidatliste1 = kandidatlisteRepository.lagre(lagKandidatliste())
        val kandidatliste2 = kandidatlisteRepository.lagre(lagKandidatliste())
        val kandidatliste3 = kandidatlisteRepository.lagre(lagKandidatliste().copy(slettet = true))

        assertThat(statistikkRepository.antallKandidatlister()).isEqualTo(2)
    }

    @Test
    fun `Tell antall kandidater`() {
        val kandidatliste1 = kandidatlisteRepository.lagre(lagKandidatliste())
        val kandidatliste2 = kandidatlisteRepository.lagre(lagKandidatliste())
        val kandidatliste3 = kandidatlisteRepository.lagre(lagKandidatliste().copy(slettet = true))

        val kandidat1 =
            kandidatlisteRepository.lagre(lagKandidat(kandidatlisteId = kandidatliste1.id!!, aktørId = "aktør1"))
        val kandidat2 =
            kandidatlisteRepository.lagre(lagKandidat(kandidatlisteId = kandidatliste2.id!!, aktørId = "aktør2"))
        val kandidat3 =
            kandidatlisteRepository.lagre(lagKandidat(kandidatlisteId = kandidatliste3.id!!, aktørId = "aktør3"))

        assertThat(statistikkRepository.antallKandidater()).isEqualTo(2)
    }

    @Test
    fun `Tell antall kandidater med vurdering`() {
        val kandidatliste1 = kandidatlisteRepository.lagre(lagKandidatliste())
        val kandidatliste2 = kandidatlisteRepository.lagre(lagKandidatliste())
        val kandidatliste3 = kandidatlisteRepository.lagre(lagKandidatliste().copy(slettet = true))

        val kandidat1 = kandidatlisteRepository.lagre(
            lagKandidat(
                kandidatlisteId = kandidatliste1.id!!,
                aktørId = "aktør1",
                AKTUELL
            )
        )
        val kandidat2 =
            kandidatlisteRepository.lagre(lagKandidat(kandidatlisteId = kandidatliste2.id!!, aktørId = "aktør2"))
        val kandidat3 = kandidatlisteRepository.lagre(
            lagKandidat(
                kandidatlisteId = kandidatliste3.id!!,
                aktørId = "aktør3",
                AKTUELL
            )
        )

        assertThat(statistikkRepository.antallKandidaterMedVurdering(Kandidat.ArbeidsgiversVurdering.AKTUELL.name)).isEqualTo(
            1
        )
    }

    private fun lagKandidatliste() = Kandidatliste(
        stillingId = UUID.randomUUID(),
        tittel = "Tittel",
        status = Kandidatliste.Status.ÅPEN,
        virksomhetsnummer = "123456789",
        uuid = UUID.fromString("7ea380f8-a0af-433f-8cbc-51c5788a7d29"),
        sistEndret = ZonedDateTime.now(),
        opprettet = ZonedDateTime.now()
    )

    private fun lagKandidat(
        kandidatlisteId: BigInteger, aktørId: String,
        arbeidsgiversVurdering: Kandidat.ArbeidsgiversVurdering = TIL_VURDERING,
    ) = Kandidat(
        uuid = UUID.randomUUID(),
        aktørId = aktørId,
        kandidatlisteId = kandidatlisteId,
        arbeidsgiversVurdering = arbeidsgiversVurdering,
        sistEndret = ZonedDateTime.now()
    )
}
