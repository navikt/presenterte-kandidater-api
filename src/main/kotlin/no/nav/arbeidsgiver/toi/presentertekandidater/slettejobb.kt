package no.nav.arbeidsgiver.toi.presentertekandidater

import java.time.ZonedDateTime
import java.util.Timer
import java.util.TimerTask

private const val antallMillisekunderIMinutt = 60000L
private const val tidTilFørsteKjøring = antallMillisekunderIMinutt
private const val tidMellomHverKjøring = antallMillisekunderIMinutt * 60

fun startPeriodiskSlettingAvKandidaterOgKandidatlister(repository: Repository) {
    val jobb = object : TimerTask() {
        override fun run() {
           slettKandidaterOgKandidatlister(repository)
        }
    }

    Timer().scheduleAtFixedRate(jobb, tidTilFørsteKjøring, tidMellomHverKjøring)
}

fun slettKandidaterOgKandidatlister(repository: Repository) {
    slettKandidater(repository)
    slettKandidatlister(repository)
}

private fun slettKandidater(repository: Repository) {
    // Slett kandidater som er sistEndret for over 6mnd siden.
}

private fun slettKandidatlister(repository: Repository) {
    val seksMånederSiden = ZonedDateTime.now().minusMonths(6)
    val kandidatlister = repository.hentTommeKandidatlisterSomIkkeErSlettetOgEldreEnn(seksMånederSiden)

    if (kandidatlister.isEmpty()) return
    log("slettejobb.kt").info("Skal slette ${kandidatlister.size} kandidatlister.")
    kandidatlister.forEach{
        repository.markerKandidatlisteSomSlettet(it.stillingId)
        log("slettejobb.kt").info("Slettet kandidatliste for stillingsId ${it.stillingId} på grunn av periodisk sletteregel.")
    }
}
