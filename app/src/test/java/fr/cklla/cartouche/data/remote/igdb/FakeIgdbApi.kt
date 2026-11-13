package fr.cklla.cartouche.data.remote.igdb

import fr.cklla.cartouche.data.remote.igdb.dto.IgdbGameDto
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbTimeToBeatDto
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import retrofit2.HttpException
import retrofit2.Response

/**
 * Double de test d'IGDB : une petite « base » de jeux qui répond aux trois formes de requêtes
 * utilisées par l'app (recherche, lecture par id, éditions et portages d'un jeu), comme le ferait
 * IGDB. Les requêtes reçues sont conservées pour être vérifiées.
 */
class FakeIgdbApi(
    private val catalog: List<IgdbGameDto>,
    private val searchResults: List<Long> = emptyList(),
    private val timeToBeat: IgdbTimeToBeatDto? = null,
) : IgdbApi {

    val queries = mutableListOf<String>()

    /** Nombre d'échecs HTTP 429 à simuler avant de répondre normalement. */
    var rateLimitFailures = 0

    /** Erreur à lever à chaque appel (simule une panne réseau). */
    var failure: Throwable? = null

    override suspend fun searchGames(clientId: String, authorization: String, query: RequestBody): List<IgdbGameDto> {
        val text = Buffer().also { query.writeTo(it) }.readUtf8()
        queries += text
        failure?.let { throw it }
        if (rateLimitFailures > 0) {
            rateLimitFailures--
            throw HttpException(Response.error<Any>(429, "".toResponseBody(null)))
        }
        return when {
            text.startsWith("search ") -> searchResults.mapNotNull { id -> catalog.firstOrNull { it.id == id } }
            "where id = (" in text -> idsIn(text, "where id = (").let { ids -> catalog.filter { it.id in ids } }
            "where version_parent = (" in text ->
                idsIn(text, "where version_parent = (").let { ids ->
                    catalog.filter { it.versionParent?.id in ids || it.parentGame?.id in ids }
                }
            else -> error("requête inattendue : $text")
        }
    }

    override suspend fun getTimeToBeat(clientId: String, authorization: String, query: RequestBody): List<IgdbTimeToBeatDto> {
        queries += Buffer().also { query.writeTo(it) }.readUtf8()
        return listOfNotNull(timeToBeat)
    }

    private fun idsIn(query: String, prefix: String): Set<Long> =
        query.substringAfter(prefix).substringBefore(")").split(",").map { it.trim().toLong() }.toSet()
}

/** Source de jeton de test : un jeton fixe, ou `null` pour simuler une authentification impossible. */
class FakeIgdbTokenSource(private val token: String? = "jeton-de-test") : IgdbTokenSource {
    override suspend fun getValidToken(): String? = token
}
