package no.nav.arbeidsgiver.toi.presentertekandidater

import java.time.ZonedDateTime
import java.util.Timer
import java.util.TimerTask

private const val antallMillisekunderIMinutt = 60000L
private const val tidTilFørsteKjøring = antallMillisekunderIMinutt
private const val tidMellomHverKjøring = antallMillisekunderIMinutt * 60
private val log = log("slettejobb.kt")

fun startPeriodiskSlettingAvKandidaterOgKandidatlister(repository: Repository) {
    val jobb = object : TimerTask() {
        override fun run() {
           slettKandidaterOgKandidatlister(repository)
        }
    }

    Timer().scheduleAtFixedRate(jobb, tidTilFørsteKjøring, tidMellomHverKjøring)
}

fun slettKandidaterOgKandidatlister(repository: Repository) {
    log.info("Starter periodisk slettejobb for kandidater og kandidatlister")
    slettKandidater(repository)
    slettKandidatlister(repository)
}

private fun slettKandidater(repository: Repository) {
    val kandidater = repository.hentKandidaterSomIkkeErEndretSiden(seksMånederSiden())

    if (kandidater.isEmpty()) return
    log.info("Skal slette ${kandidater.size} kandidater")
    kandidater.forEach{ kandidat ->
        repository.slettKandidat(kandidat.id!!)
        log.info("Slettet kandidat med aktørId ${kandidat.aktørId} for kandidatlisteId ${kandidat.kandidatlisteId} på grunn av periodisk sletteregel.")
    }
}

private fun slettKandidatlister(repository: Repository) {
    val kandidatlister = repository.hentTommeKandidatlisterSomIkkeErSlettetOgEldreEnn(seksMånederSiden())

    if (kandidatlister.isEmpty()) return
    log.info("Skal slette ${kandidatlister.size} kandidatlister.")
    kandidatlister.forEach{
        repository.markerKandidatlisteSomSlettet(it.stillingId)
        log.info("Slettet kandidatliste for stillingsId ${it.stillingId} på grunn av periodisk sletteregel.")
    }
}

private fun seksMånederSiden(): ZonedDateTime = ZonedDateTime.now().minusMonths(6)
