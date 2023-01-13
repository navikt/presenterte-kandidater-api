package no.nav.arbeidsgiver.toi.presentertekandidater.altinn

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.ProxyConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyKlientException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.*
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.Cache.AltinnFiltrering.ENKELTRETTIGHET_REKRUTTERING
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.Cache.AltinnFiltrering.INGEN
import no.nav.arbeidsgiver.toi.presentertekandidater.log
import no.nav.arbeidsgiver.toi.presentertekandidater.secureLog
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.TokendingsKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.variable
import java.time.Duration


class AltinnKlient(
    envs: Map<String, String>,
    private val tokendingsKlient: TokendingsKlient,
) {
    private val consumerId = envs.variable("NAIS_APP_NAME")
    private val altinnProxyUrl = envs.variable("ALTINN_PROXY_URL")
    private val scope = envs.variable("ALTINN_PROXY_AUDIENCE")
    private val config = AltinnrettigheterProxyKlientConfig(ProxyConfig(consumerId, altinnProxyUrl))
    private val klient = AltinnrettigheterProxyKlient(config)
    private val rekrutteringsrettighetAltinnKode = "5078"
    private val rekrutteringsrettighetAltinnServiceEdition = "1"
    private val cacheLevetid = Duration.ofMinutes(15)
    private val cache = Cache(levetid = cacheLevetid)

    fun hentOrganisasjonerMedRettighetRekrutteringFraAltinn(fnr: String, accessToken: String): List<AltinnReportee> {
        log.info("Skal hente organisasjoner for innlogget person")

        val cachetOrganisasjoner = cache.hentFraCache(fnr, ENKELTRETTIGHET_REKRUTTERING)
        if (cachetOrganisasjoner != null) return cachetOrganisasjoner

        val exchangeToken = tokendingsKlient.veksleInnToken(accessToken, scope)

        try {
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
                    cache.leggICache(fnr, it, ENKELTRETTIGHET_REKRUTTERING)
                }
            }
        } catch (e: AltinnrettigheterProxyKlientException) {
            log.error("Kall mot AltinnProxy med filtrering på enkeltrettighet rekruttering feilet. Se SecureLog for stacktrace.")
            secureLog.error("Kall mot AltinnProxy med filtrering på enkeltrettighet rekruttering feilet.", e)
            throw e
        }
    }

    fun hentOrganisasjoner(fnr: String, accessToken: String): List<AltinnReportee> {
        val cachetOrganisasjoner = cache.hentFraCache(fnr, INGEN)
        if (cachetOrganisasjoner != null) return cachetOrganisasjoner

        val exchangeToken = tokendingsKlient.veksleInnToken(accessToken, scope)

        try {
            return klient.hentOrganisasjoner(
                selvbetjeningToken = SelvbetjeningToken(exchangeToken),
                subject = Subject(fnr),
                filtrerPåAktiveOrganisasjoner = true
            ).also {
                if (it.isEmpty()) {
                    log.info("Innlogget person representerer ingen organisasjoner")
                } else {
                    log.info("Innlogget person representerer ${it.size} organisasjoner")
                    cache.leggICache(fnr, it, INGEN)
                }
            }
        } catch (e: AltinnrettigheterProxyKlientException) {
            log.error("Kall mot AltinnProxy uten filtrering på enkeltrettighet feilet. Se SecureLog for stacktrace.")
            secureLog.error("Kall mot AltinnProxy uten filtrering på enkeltrettighet feilet", e)
            throw e
        }

    }
}
