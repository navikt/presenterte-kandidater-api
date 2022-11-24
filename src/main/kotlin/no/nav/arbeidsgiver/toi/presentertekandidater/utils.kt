package no.nav.arbeidsgiver.toi.presentertekandidater

import org.slf4j.Logger
import org.slf4j.LoggerFactory

val Any.log: Logger
    get() = LoggerFactory.getLogger(this::class.java)

fun log(name: String): Logger = LoggerFactory.getLogger(name)

fun Map<String, String>.variable(felt: String) = this[felt] ?: error("$felt er ikke angitt")
