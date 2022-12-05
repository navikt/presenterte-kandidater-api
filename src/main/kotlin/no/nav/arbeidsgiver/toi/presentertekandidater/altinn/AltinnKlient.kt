package no.nav.arbeidsgiver.toi.presentertekandidater.altinn

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.ProxyConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.*
import no.nav.arbeidsgiver.toi.presentertekandidater.log
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.TokendingsKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.variable
import java.time.ZonedDateTime


class AltinnKlient(
    envs: Map<String, String>,
    private val tokendingsKlient: TokendingsKlient
) {
    private val consumerId = envs.variable("NAIS_APP_NAME")
    private val altinnProxyUrl = envs.variable("ALTINN_PROXY_URL")
    private val scope = envs.variable("ALTINN_PROXY_AUDIENCE")
    private val config = AltinnrettigheterProxyKlientConfig(ProxyConfig(consumerId, altinnProxyUrl))
    private val klient = AltinnrettigheterProxyKlient(config)
    private val cache = hashMapOf<String, CachetOrganisasjoner>()
    private val cacheLevetidMinutter = 15L
    private val rekrutteringsrettighetAltinnKode = "5078"
    private val rekrutteringsrettighetAltinnServiceEdition = "1"

    fun hentOrganisasjonerMedRettighetRekruttering(fnr: String, accessToken: String): List<AltinnReportee> {
        log.info("Skal hente organisasjoner for innlogget person")
        val fraCache = cache[fnr]

        return if (fraCache != null && !fraCache.harUtløpt()) {
            log.info("Har cache med organisasjoner for innlogget person")
            fraCache.organisasjoner
        } else {
            hentOrganisasjonerMedRettighetRekrutteringFraAltinn(fnr, accessToken)
        }
    }

    private fun hentOrganisasjonerMedRettighetRekrutteringFraAltinn(fnr: String, accessToken: String): List<AltinnReportee> {
        val exchangeToken = tokendingsKlient.veksleInnToken(accessToken, scope)

        return klient.hentOrganisasjoner(
            selvbetjeningToken = SelvbetjeningToken(exchangeToken),
            subject = Subject(fnr),
            serviceCode = ServiceCode(rekrutteringsrettighetAltinnKode),
            serviceEdition = ServiceEdition(rekrutteringsrettighetAltinnServiceEdition),
            filtrerPåAktiveOrganisasjoner = true
        ).also {
            if (it.isEmpty()) {
                log.info("Innlogget person representerer ingen organisasjoner")
            } else {
                log.info("Innlogget person representerer ${it.size} organisasjoner")
                leggICache(fnr, it)
            }
        }
    }

    private fun leggICache(fnr: String, organisasjoner: List<AltinnReportee>) {
        cache[fnr] = CachetOrganisasjoner(
            organisasjoner = organisasjoner,
            utløper = ZonedDateTime.now().plusMinutes(cacheLevetidMinutter)
        )
    }

    private fun CachetOrganisasjoner.harUtløpt() = ZonedDateTime.now().isAfter(utløper.plusMinutes(cacheLevetidMinutter))

    data class CachetOrganisasjoner(
        val organisasjoner: List<AltinnReportee>,
        val utløper: ZonedDateTime
    )
}
