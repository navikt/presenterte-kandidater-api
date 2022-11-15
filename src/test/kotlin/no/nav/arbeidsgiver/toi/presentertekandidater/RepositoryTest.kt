package no.nav.arbeidsgiver.toi.presentertekandidater

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.assertNotNull

internal class RepositoryTest {
    
    val repository = opprettTestRepositoryMedLokalPostgres()

    @Test
    fun `Persistering og henting av kandidatliste går OK`() {
        val kandidatliste = lagKandidatliste()
        repository.lagre(kandidatliste)
        repository.hentKandidatliste(kandidatliste.stillingId).apply {
            assertThat(this?.id).isNotNull
            assertThat(this?.tittel).isEqualTo(kandidatliste.tittel)
            assertThat(this?.status).isEqualTo(kandidatliste.status)
            assertThat(this?.virksomhetsnummer).isEqualTo(kandidatliste.virksomhetsnummer)
        }
    }

    @Test
    fun `Persistering og henting av kandidat går OK`() {
        val kandidatliste = lagKandidatliste()
        repository.lagre(kandidatliste)
        val lagretKandidatliste = repository.hentKandidatliste(kandidatliste.stillingId)

        val uuid = UUID.randomUUID()

        val kandidat = Kandidat(
            aktørId = "1234567891012",
            kandidatlisteId = lagretKandidatliste!!.id!!,
            uuid = uuid
        )
        repository.lagre(kandidat)

        repository.hentKandidat(kandidat.aktørId, lagretKandidatliste.id!!).apply {
            assertThat(this?.aktørId).isEqualTo(kandidat.aktørId)
            assertThat(this?.kandidatlisteId).isEqualTo(kandidat.kandidatlisteId)
            assertThat(this?.uuid).isEqualTo(uuid)
        }
    }

    @Test
    fun `Henting av kandidatlister med antall hvor listen har kandidater`() {
        val kandidatliste = lagKandidatliste()
        repository.lagre(kandidatliste)
        val lagretKandidatliste = repository.hentKandidatliste(kandidatliste.stillingId)
        val kandidater = listOf(
            Kandidat(
                aktørId = "1234567891012",
                kandidatlisteId = lagretKandidatliste?.id!!,
                uuid = UUID.randomUUID()
            ),
            Kandidat(
                aktørId = "2234567891012",
                kandidatlisteId = lagretKandidatliste.id!!,
                uuid = UUID.randomUUID()
            )
        )
        kandidater.forEach { repository.lagre(it)}

        val kandidatlister = repository.hentKandidatlisterMedAntall(kandidatliste.virksomhetsnummer)
        assertThat(kandidatlister.size).isEqualTo(1)
        assertThat(kandidatlister[0].antallKandidater).isEqualTo(2)
    }

    @Test
    fun `Henting av kandidatlister med antall hvor listen IKKE har kandidater`() {
        val kandidatliste = lagKandidatliste()
        repository.lagre(kandidatliste)
        val kandidatlister = repository.hentKandidatlisterMedAntall(kandidatliste.virksomhetsnummer)
        assertThat(kandidatlister.size).isEqualTo(1)
        assertThat(kandidatlister[0].antallKandidater).isEqualTo(0)
    }

    @Test
    fun `Henting av kandidatliste med kandidater`() {
        val kandidatliste = lagKandidatliste()
        repository.lagre(kandidatliste)
        val lagretKandidatliste = repository.hentKandidatliste(kandidatliste.stillingId)
        val kandidatUUID = UUID.randomUUID()
        repository.lagre(
            Kandidat(
                aktørId = "test",
                kandidatlisteId = lagretKandidatliste?.id!!,
                uuid = kandidatUUID
            )
        )

        val listeMedKandidater = repository.hentKandidatliste(kandidatliste.stillingId)
        val kandidater = repository.hentKandidater(listeMedKandidater?.id!!)
        assertThat(listeMedKandidater).isNotNull
        assertThat(kandidater?.size).isEqualTo(1)
        assertThat(kandidater!![0].kandidatlisteId).isEqualTo(lagretKandidatliste.id)
        assertThat(kandidater[0].uuid).isEqualTo(kandidatUUID)
    }

    @Test
    fun `Henting av kandidatliste med tom liste kandidater`() {
        val kandidatliste = lagKandidatliste()
        repository.lagre(kandidatliste)
        val listeMedKandidater = repository.hentKandidatliste(kandidatliste.stillingId)
        val kandidater = repository.hentKandidater(listeMedKandidater?.id!!)
        assertThat(listeMedKandidater).isNotNull
        assertThat(kandidater).isEmpty()
    }

    @Test
    fun `Skal ikke kunne lagre to kandidatlister med samme stillingsId`() {
        val fellesStillingid = UUID.randomUUID()

        val kandidatliste1 = Kandidatliste(
            stillingId = fellesStillingid,
            tittel = "Tittel",
            status = Kandidatliste.Status.ÅPEN,
            virksomhetsnummer = "123456789",
            uuid = UUID.fromString("7ea380f8-a0af-433f-8cbc-51c5788a7d29"),
            sistEndret = ZonedDateTime.now(),
            opprettet = ZonedDateTime.now()
        )

        val kandidatliste2 = Kandidatliste(
            stillingId = fellesStillingid,
            tittel = "Tittel2",
            status = Kandidatliste.Status.LUKKET,
            virksomhetsnummer = "123456780",
            uuid = UUID.fromString("7ea380f8-a0af-433f-8cbc-51c5788a7d28"),
            sistEndret = ZonedDateTime.now(),
            opprettet = ZonedDateTime.now()
        )


        repository.lagre(kandidatliste1)
 
        assertThatThrownBy{
            repository.lagre(kandidatliste2)
        }.isInstanceOf(PSQLException::class.java)

    }

    @Test
    fun `Skal kunne lagre samme kandidat i to lister`() {
        val aktørID = "123456789"
        val kandidatliste1 = lagKandidatliste()
        val kandidatliste2 = lagKandidatliste()
        repository.lagre(kandidatliste1)
        repository.lagre(kandidatliste2)
        val lagretKandidatliste1 = repository.hentKandidatliste(kandidatliste1.stillingId)
        val lagretKandidatliste2 = repository.hentKandidatliste(kandidatliste2.stillingId)
        val kandidatPåListe1 = lagKandidat(lagretKandidatliste1?.id!!, aktørID)
        val kandidatPåListe2 = lagKandidat(lagretKandidatliste2?.id!!, aktørID)

        repository.lagre(kandidatPåListe1)
        repository.lagre(kandidatPåListe2)

        val kandidatliste1MedKandidater = repository.hentKandidatliste(kandidatliste1.stillingId)
        val kandidater1 = repository.hentKandidater(kandidatliste1MedKandidater?.id!!)
        val kandidatliste2MedKandidater = repository.hentKandidatliste(kandidatliste2.stillingId)
        val kandidater2 = repository.hentKandidater(kandidatliste2MedKandidater?.id!!)
        assertThat(kandidater1).hasSize(1)
        assertThat(kandidater2).hasSize(1)
        assertThat(kandidater1.first().aktørId).isEqualTo(aktørID)
        assertThat(kandidater2.first().aktørId).isEqualTo(aktørID)
    }

    @Test
    fun `Datofelt skal lagres med riktig tidssone`() {
        val kandidatliste = lagKandidatliste()

        repository.lagre(kandidatliste)

        val lagretKandidatliste = repository.hentKandidatliste(kandidatliste.stillingId)
        assertNotNull(lagretKandidatliste)
        assertThat(lagretKandidatliste.sistEndret).isEqualToIgnoringSeconds(Instant.now().atZone(ZoneId.of(("Europe/Oslo"))))
        assertThat(lagretKandidatliste.opprettet).isEqualToIgnoringSeconds(Instant.now().atZone(ZoneId.of(("Europe/Oslo"))))
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

    private fun lagKandidat(kandidatlisteId: BigInteger, aktørId: String) = Kandidat(
        uuid = UUID.randomUUID(),
        aktørId = aktørId,
        kandidatlisteId = kandidatlisteId
    )
}
