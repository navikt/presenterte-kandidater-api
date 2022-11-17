package no.nav.arbeidsgiver.toi.presentertekandidater

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.http.Context
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.Rolle
import java.util.*

fun startKandidatlisteController(javalin: Javalin, repository: Repository, opensearchKlient: OpenSearchKlient) {
    javalin.routes {
        get("/kandidatlister", hentKandidatlister(repository), Rolle.ARBEIDSGIVER)
        get("/kandidatliste/{stillingId}", hentKandidatliste(repository, opensearchKlient), Rolle.ARBEIDSGIVER)
    }
}

private val hentKandidatlister: (repository: Repository) -> (Context) -> Unit = { repository ->
    { context ->
        val virksomhetsnummer = context.queryParam("virksomhetsnummer")

        if (virksomhetsnummer.isNullOrBlank()) {
            context.status(400)
        } else {
            val lister: KandidatlisterDto = repository.hentKandidatlisterMedAntall(virksomhetsnummer)
            context.json(lister).status(200)
        }
    }
}

private val hentKandidatliste: (repository: Repository, opensearchKlient: OpenSearchKlient) -> (Context) -> Unit =
    { repository, opensearchKlient ->
        { context ->
            val stillingId = context.pathParam("stillingId")

            if (stillingId.isNullOrBlank()) {
                context.status(400)
            } else {
                val kandidatliste = repository.hentKandidatliste(UUID.fromString(stillingId))

                if (kandidatliste == null) {
                    context.status(404)
                } else {
                    val kandidater = repository.hentKandidater(kandidatliste.id!!)
                    val cver = opensearchKlient.hentCver(kandidater.map { it.aktørId })
                    val kandidatDtoer = kandidater.map { KandidatDto(it, cver[it.aktørId]) }

                    context.json(KandidatlisteDto(kandidatliste, kandidatDtoer)).status(200)
                }
            }
        }
    }

data class KandidatDto(
    val kandidat: Kandidat, val cv: Cv?
)

data class KandidatlisteDto(
    val kandidatliste: Kandidatliste, val kandidater: List<KandidatDto>
)

typealias KandidatlisterDto = List<KandidatlisteMedAntallKandidater>
