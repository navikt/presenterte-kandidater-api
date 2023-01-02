package no.nav.arbeidsgiver.toi.presentertekandidater.altinn

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import java.time.Duration
import java.time.ZonedDateTime

class Cache(private val levetid: Duration) {
    private val cache = hashMapOf<Nøkkel, CachetOrganisasjoner>()

    fun leggICache(fødelsnummer: String, organisasjoner: List<AltinnReportee>, filtrering: AltinnFiltrering) {
        require(organisasjoner.isNotEmpty()) { "Liste av organisasjoner kan ikke være tom" }
        cache[Nøkkel(fødelsnummer, filtrering)] = CachetOrganisasjoner(
            organisasjoner = organisasjoner,
            utløper = ZonedDateTime.now().plus(levetid)
        )
    }

    fun hentFraCache(fødselsnummer: String, filtrering: AltinnFiltrering): List<AltinnReportee>? {
        val nøkkel = Nøkkel(fødselsnummer, filtrering)
        val cachetOrganisasjoner = cache[nøkkel] ?: return null

        val harUtløpt = ZonedDateTime.now().isAfter(cachetOrganisasjoner.utløper.plus(levetid))

        return if (harUtløpt) {
            cache.remove(nøkkel)
            null
        } else {
            cachetOrganisasjoner.organisasjoner
        }
    }

    private data class CachetOrganisasjoner(
        val organisasjoner: List<AltinnReportee>,
        val utløper: ZonedDateTime,
    )

    private data class Nøkkel(
        val fødselsnummer: String,
        val filtrering: AltinnFiltrering,
    )

    enum class AltinnFiltrering {
        INGEN,
        ENKELTRETTIGHET_REKRUTTERING
    }
}