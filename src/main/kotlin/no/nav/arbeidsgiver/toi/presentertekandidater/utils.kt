package no.nav.arbeidsgiver.toi.presentertekandidater

import io.javalin.http.Context
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val Any.log: Logger
    get() = LoggerFactory.getLogger(this::class.java)

fun log(name: String): Logger = LoggerFactory.getLogger(name)

fun Map<String, String>.variable(felt: String) = this[felt] ?: error("$felt er ikke angitt")

fun Context.hentFødselsnummer(): String = attribute("fnr") ?: error("Context har ikke fødselsnummer")

fun Context.setFødselsnummer(fnr: String) = attribute("fnr", fnr)