package no.nav.arbeidsgiver.toi.presentertekandidater

import org.junit.jupiter.api.*
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlettejobbTest {
    private val repository = opprettTestRepositoryMedLokalPostgres()

    @Test
    @Disabled
    fun `Slettejobb skal starte et minutt etter oppstart`() {
        val kandidatliste = Testdata.lagGyldigKandidatliste(UUID.randomUUID()).copy(
            sistEndret = ZonedDateTime.now().minusMonths(6)
        )

        repository.lagre(kandidatliste)

        startLocalApplication(repository = repository)

        var constantClock: Clock =
            Clock.fixed(ZonedDateTime.now().plusSeconds(50).toInstant(), ZoneId.systemDefault())

        assertNotNull(repository.hentKandidatliste(kandidatliste.stillingId))

        constantClock =
            Clock.fixed(ZonedDateTime.now().plusSeconds(10).toInstant(), ZoneId.systemDefault())

        assertNull(repository.hentKandidatliste(kandidatliste.stillingId))

        Clock.offset(constantClock, Duration.ZERO)
    }
}