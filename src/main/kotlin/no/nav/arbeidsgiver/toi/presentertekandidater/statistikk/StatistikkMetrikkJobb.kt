package no.nav.arbeidsgiver.toi.presentertekandidater.statistikk

import io.micrometer.prometheus.PrometheusMeterRegistry
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class StatistikkMetrikkJobb(private val statistikkRepository: StatistikkRepository,
                            private val meterRegistry: PrometheusMeterRegistry) {

    companion object {
        val LOG = LoggerFactory.getLogger(StatistikkMetrikkJobb::class.java)
    }

    val antallKandidatlister = AtomicLong(0)
    val antallKandidater = AtomicLong(0)

    val antallKandidatlisterGauge = meterRegistry.gauge("antall_kandidatlister", antallKandidatlister)
    val antallKandidaterGauge = meterRegistry.gauge("antall_kandidater", antallKandidater)

    val executor = Executors.newScheduledThreadPool(1)

    fun start() {
        executor.scheduleWithFixedDelay({hentStatistikk()}, 5L, 20L, TimeUnit.SECONDS)
    }

    fun stopp() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(2L, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (ie: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }

    }

    private fun hentStatistikk() {
        try {
            antallKandidatlister.getAndSet(statistikkRepository.antallKandidatlister().toLong())
            antallKandidater.getAndSet(statistikkRepository.antallKandidater().toLong())
        } catch (e: Exception) {
            LOG.warn("Problemer med å hente statistikk: ${e.message}", e)
        }
    }

}