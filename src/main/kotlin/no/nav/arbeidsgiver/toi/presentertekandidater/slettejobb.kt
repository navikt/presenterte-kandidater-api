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
    val kandidater = repository.hentKandidaterSomIkkeErEndretSiden(seksMånederSiden())

    if (kandidater.isEmpty()) return
    log("slettejobb.kt").info("Skal slette ${kandidater.size} kandidater")
    kandidater.forEach{ kandidat ->
        repository.slettKandidatFraKandidatliste(kandidat.aktørId, kandidat.kandidatlisteId)
        log("slettejobb.kt").info("Slettet kandidat med aktørId ${kandidat.aktørId} for kandidatlisteId ${kandidat.kandidatlisteId} på grunn av periodisk sletteregel.")
    }
}

private fun slettKandidatlister(repository: Repository) {
    val kandidatlister = repository.hentTommeKandidatlisterSomIkkeErSlettetOgEldreEnn(seksMånederSiden())

    if (kandidatlister.isEmpty()) return
    log("slettejobb.kt").info("Skal slette ${kandidatlister.size} kandidatlister.")
    kandidatlister.forEach{
        repository.markerKandidatlisteSomSlettet(it.stillingId)
        log("slettejobb.kt").info("Slettet kandidatliste for stillingsId ${it.stillingId} på grunn av periodisk sletteregel.")
    }
}

private fun seksMånederSiden(): ZonedDateTime = ZonedDateTime.now().minusMonths(6)
