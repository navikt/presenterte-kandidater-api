package no.nav.arbeidsgiver.toi.presentertekandidater

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.http.Context
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.Rolle
import java.util.*

fun startKandidatlisteController(javalin: Javalin, repository: Repository, opensearchKlient: OpenSearchKlient) {
    javalin.routes {
        get(
            "/kandidatliste/{stillingId}",
            hentKandidatlistesammendrag(repository, opensearchKlient),
            Rolle.ARBEIDSGIVER
        )
        get("/kandidatlister", hentKandidatlister(repository), Rolle.ARBEIDSGIVER)
        get("/kandidatliste/{stillingsUid}/kandidat/{uuid}", hentKandidat(repository, opensearchKlient), Rolle.ARBEIDSGIVER)
    }
}

//TODO husk å sjekke om arbeidsgiver har arbeidssøker på en av sine kandidatlister
private val hentKandidat: (repository: Repository, opensearchKlient: OpenSearchKlient) -> (Context) -> Unit =
    { repository, opensearchKlient ->
        { context ->
                val kandidatuuid = context.pathParamMap().get("uuid")
                val stillingsUid = context.pathParamMap().get("stillingsUid")
                val kandidat = repository.hentKandidatMedUUID(UUID.fromString(kandidatuuid))
                val cv = opensearchKlient.hentCv(kandidat?.aktørId!!)
                context.json(KandidatDto(kandidat, cv))
        }
    }

data class KandidatDto (
    val kandidat: Kandidat?,
    val cv: OpensearchData.Cv?
)



private val hentKandidatlister: (repository: Repository) -> (Context) -> Unit = { repository ->
    { context ->
        val virksomhetsnummer = context.queryParam("virksomhetsnummer")
        if (virksomhetsnummer.isNullOrBlank()) {
            context.status(400)
        } else {
            val lister = repository.hentKandidatlisterMedAntall(virksomhetsnummer)
            context.json(lister).status(200)
        }
    }
}

private val hentKandidatlistesammendrag: (repository: Repository, opensearchKlient: OpenSearchKlient) -> (Context) -> Unit =
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
                    val aktørider = kandidater.map { it.aktørId }
                    val cver = opensearchKlient.hentSammendragForCver(aktørider)
                    val sammendragForKandidater =
                        kandidater.map { Kandidatsammendrag(kandidat = it, cv = cver[it.aktørId]) }
                    val kandidatlistesammendrag =
                        Kandidatlistesammendrag(kandidatliste = kandidatliste, kandidater = sammendragForKandidater)
                    context.json(kandidatlistesammendrag).status(200)
                }
            }
        }
    }

