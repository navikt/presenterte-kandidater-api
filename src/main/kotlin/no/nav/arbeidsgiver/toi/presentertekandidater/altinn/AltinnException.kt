package no.nav.arbeidsgiver.toi.presentertekandidater.altinn

sealed class AltinnException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class AltinnTilgangException : AltinnException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable?) : super(message, cause)
}

class AltinnServiceException : AltinnException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable?) : super(message, cause)
}