package no.nav.arbeidsgiver.toi.presentertekandidater.altinn


import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.Cache.AltinnFiltrering.NAV_REKRUTTERING_KANDIDATER
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.Cache.AltinnFiltrering.INGEN
import no.nav.arbeidsgiver.toi.presentertekandidater.tilfeldigFødselsnummer
import no.nav.arbeidsgiver.toi.presentertekandidater.tilfeldigVirksomhetsnummer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import kotlin.test.assertNotNull
import kotlin.test.assertNull


class CacheTest {
    val cacheLevetid = Duration.ofMinutes(15)
    val cache = Cache(cacheLevetid)

    @Test
    fun `Caching fungerer med filtrering på rettighet nav_rekruttering_kandidater`() {
        val filtrering = NAV_REKRUTTERING_KANDIDATER
        val fødselsnummer = tilfeldigFødselsnummer()
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon(orgNummer = tilfeldigVirksomhetsnummer()),
            Testdata.lagAltinnOrganisasjon(orgNummer = tilfeldigVirksomhetsnummer()),
        )
        cache.leggICache(fødselsnummer, organisasjoner, filtrering)

        val organisasjonerFraCache = cache.hentFraCache(fødselsnummer, filtrering)

        assertNotNull(organisasjonerFraCache)
        assertThat(organisasjonerFraCache).isEqualTo(organisasjoner)
    }

    @Test
    fun `Caching fungerer med filtrering "ingen"`() {
        val filtrering = INGEN
        val fødselsnummer = tilfeldigFødselsnummer()
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon(orgNummer = tilfeldigVirksomhetsnummer()),
            Testdata.lagAltinnOrganisasjon(orgNummer = tilfeldigVirksomhetsnummer()),
        )
        cache.leggICache(fødselsnummer, organisasjoner, filtrering)

        val organisasjonerFraCache = cache.hentFraCache(fødselsnummer, filtrering)

        assertNotNull(organisasjonerFraCache)
        assertThat(organisasjonerFraCache).isEqualTo(organisasjoner)
    }

    @Test
    fun `Tillatter ikke caching av tom liste med organisasjoner`() {
        val fødselsnummer = tilfeldigFødselsnummer()
        assertThrows<IllegalArgumentException> {
            cache.leggICache(fødselsnummer, emptyList(), INGEN)
        }
        assertThrows<IllegalArgumentException> {
            cache.leggICache(fødselsnummer, emptyList(), NAV_REKRUTTERING_KANDIDATER)
        }
    }

    @Test
    fun `Henting fra cache med én altinnfiltrering skal ikke returnere resultat fra annen filtrering`() {
        val fødselsnummer = tilfeldigFødselsnummer()
        val organisasjonerUtenFiltrering = Testdata.lagAltinnOrganisasjon(orgNummer = tilfeldigVirksomhetsnummer())
        cache.leggICache(fødselsnummer, listOf(organisasjonerUtenFiltrering), INGEN)

        val fraCache = cache.hentFraCache(fødselsnummer, NAV_REKRUTTERING_KANDIDATER)

        assertNull(fraCache)
    }

    @Test
    fun `Skal ikke returnere en annen persons cachete organisasjoner`() {
        val fødselsnummer = tilfeldigFødselsnummer()
        val organisasjonerUtenFiltrering = Testdata.lagAltinnOrganisasjon(orgNummer = tilfeldigVirksomhetsnummer())
        cache.leggICache(fødselsnummer, listOf(organisasjonerUtenFiltrering), INGEN)

        val etAnnetFødselsnummer = tilfeldigFødselsnummer()
        val fraCache = cache.hentFraCache(etAnnetFødselsnummer, INGEN)

        assertNull(fraCache)
    }

    @Test
    fun `Returnerer ikke fra cache hvis levetid er utløpt`() {
        val cacheMedLavLevetid = Cache(levetid = Duration.ofMillis(0))
        val fødselsnummer = tilfeldigFødselsnummer()
        val organisasjonerUtenFiltrering = Testdata.lagAltinnOrganisasjon(orgNummer = tilfeldigVirksomhetsnummer())
        cacheMedLavLevetid.leggICache(fødselsnummer, listOf(organisasjonerUtenFiltrering), INGEN)

        val cacheteOrganisasjoner = cacheMedLavLevetid.hentFraCache(fødselsnummer, INGEN)

        assertNull(cacheteOrganisasjoner)
    }
}