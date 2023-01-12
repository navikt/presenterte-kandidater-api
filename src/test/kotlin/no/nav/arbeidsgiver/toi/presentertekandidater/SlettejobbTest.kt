package no.nav.arbeidsgiver.toi.presentertekandidater

import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.slettKandidaterOgKandidatlister
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlettejobbTest {
    private val repository = kandidatlisteRepositoryMedLokalPostgres()

    @BeforeAll
    fun beforeAll() {
        kjørFlywayMigreringer(dataSource)
    }

    @Test
    fun `Slettejobb skal slette tomme kandidatlister som ikke er endret på 6mnd`() {
        var kandidatlisteSomSkalSlettes = kandidatliste(UUID.randomUUID()).copy(
            sistEndret = ZonedDateTime.now().minusMonths(6)
        )
        var kandidatlisteSomIkkeSkalSlettes = kandidatliste(UUID.randomUUID()).copy(
            sistEndret = ZonedDateTime.now().minusMonths(5).minusDays(15)
        )
        repository.lagre(kandidatlisteSomSkalSlettes)
        repository.lagre(kandidatlisteSomIkkeSkalSlettes)

        kandidatlisteSomSkalSlettes = repository.hentKandidatliste(kandidatlisteSomSkalSlettes.stillingId)!!
        kandidatlisteSomIkkeSkalSlettes = repository.hentKandidatliste(kandidatlisteSomIkkeSkalSlettes.stillingId)!!
        assertFalse(kandidatlisteSomSkalSlettes.slettet)
        assertFalse(kandidatlisteSomIkkeSkalSlettes.slettet)

        slettKandidaterOgKandidatlister(repository)

        kandidatlisteSomSkalSlettes = repository.hentKandidatliste(kandidatlisteSomSkalSlettes.stillingId)!!
        kandidatlisteSomIkkeSkalSlettes = repository.hentKandidatliste(kandidatlisteSomIkkeSkalSlettes.stillingId)!!
        assertTrue(kandidatlisteSomSkalSlettes.slettet)
        assertFalse(kandidatlisteSomIkkeSkalSlettes.slettet)
    }

    @Test
    fun `Slettejobb skal slette kandidater som ikke er endret på 6mnd`() {
        var kandidatliste = kandidatliste(UUID.randomUUID()).copy(
            sistEndret = ZonedDateTime.now()
        )
        repository.lagre(kandidatliste)
        kandidatliste = repository.hentKandidatliste(kandidatliste.stillingId)!!

        val kandidatSomSkalSlettes = Testdata.lagKandidatTilKandidatliste(kandidatliste.id!!)
            .copy(sistEndret = ZonedDateTime.now().minusMonths(6))
        val kandidatSomIkkeSkalSlettes = Testdata.lagKandidatTilKandidatliste(kandidatliste.id!!)
            .copy(sistEndret = ZonedDateTime.now().minusMonths(5))
        repository.lagre(kandidatSomSkalSlettes)
        repository.lagre(kandidatSomIkkeSkalSlettes)

        assertEquals(2, repository.hentKandidater(kandidatliste.id!!).size)

        slettKandidaterOgKandidatlister(repository)

        assertEquals(1, repository.hentKandidater(kandidatliste.id!!).size)
    }

    @Test
    fun `Slettejobb skal ikke slette gamle kandidatlister med kandidater`() {
        var kandidatliste = kandidatliste(UUID.randomUUID()).copy(
            sistEndret = ZonedDateTime.now().minusMonths(8)
        )
        repository.lagre(kandidatliste)
        kandidatliste = repository.hentKandidatliste(kandidatliste.stillingId)!!

        var nyKandidat = Testdata.lagKandidatTilKandidatliste(kandidatliste.id!!)
        repository.lagre(nyKandidat)

        slettKandidaterOgKandidatlister(repository)

        assertEquals(1, repository.hentKandidater(kandidatliste.id!!).size)
        assertNotNull(repository.hentKandidatliste(kandidatliste.stillingId))
    }
}
