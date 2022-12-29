package no.nav.arbeidsgiver.toi.presentertekandidater.statistikk

import io.micrometer.core.instrument.Tags
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class StatistikkMetrikkJobb(private val statistikkRepository: StatistikkRepository,
                            private val meterRegistry: PrometheusMeterRegistry) {

    companion object {
        val LOG = LoggerFactory.getLogger(StatistikkMetrikkJobb::class.java)
    }

    private val antallKandidatlister =  meterRegistry.gauge("antall_kandidatlister", AtomicLong(0))
    private val antallKandidater = meterRegistry.gauge("antall_kandidater", AtomicLong(0))
    private val kandidatVurderinger = mutableMapOf<String, AtomicLong>()

    init {
        Kandidat.ArbeidsgiversVurdering.values().asSequence().forEach { v ->
            kandidatVurderinger[v.name] = meterRegistry.gauge("antall_kandidatvurderinger",
                Tags.of("vurdering", v.name), AtomicLong(0)) as AtomicLong
        }
    }
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

            kandidatVurderinger.keys.forEach { k ->
                kandidatVurderinger[k]?.getAndSet(statistikkRepository.antallKandidaterMedVurdering(k).toLong())
            }
        } catch (e: Exception) {
            LOG.warn("Problemer med å hente statistikk: ${e.message}", e)
        }
    }
}