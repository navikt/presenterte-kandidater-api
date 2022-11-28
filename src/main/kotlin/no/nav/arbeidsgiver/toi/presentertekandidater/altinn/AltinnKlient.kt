package no.nav.arbeidsgiver.toi.presentertekandidater.altinn

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.ProxyConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.SelvbetjeningToken
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
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
    val config = AltinnrettigheterProxyKlientConfig(ProxyConfig(consumerId, altinnProxyUrl))
    val klient = AltinnrettigheterProxyKlient(config)
    val cache = hashMapOf<String, CachetOrganisasjoner>()
    val levetidMinutter = 15L

    fun hentOrganisasjoner(fnr: String, accessToken: String): List<AltinnReportee> {
        val fraCache = cache[fnr]

        return if (fraCache != null && !fraCache.harUtløpt()) {
            fraCache.organisasjoner
        } else {
            hentOrganisasjonerFraAltinn(fnr, accessToken)
        }
    }

    private fun hentOrganisasjonerFraAltinn(fnr: String, accessToken: String): List<AltinnReportee> {
        val exchangeToken = tokendingsKlient.veksleInnToken(accessToken, scope)

        return klient.hentOrganisasjoner(
            SelvbetjeningToken(exchangeToken),
            Subject(fnr),
            true
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
            utløper = ZonedDateTime.now().plusMinutes(levetidMinutter)
        )
    }

    private fun CachetOrganisasjoner.harUtløpt() = ZonedDateTime.now().isAfter(utløper.plusMinutes(levetidMinutter))

    data class CachetOrganisasjoner(
        val organisasjoner: List<AltinnReportee>,
        val utløper: ZonedDateTime
    )
}
