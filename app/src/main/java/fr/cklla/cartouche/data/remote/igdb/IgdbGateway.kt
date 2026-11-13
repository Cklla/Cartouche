package fr.cklla.cartouche.data.remote.igdb

import fr.cklla.cartouche.BuildConfig
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbGameDto
import fr.cklla.cartouche.data.remote.igdb.dto.IgdbTimeToBeatDto
import fr.cklla.cartouche.domain.model.igdbIdOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/** IGDB est injoignable ou refuse l'accès : identifiants absents, jeton Twitch impossible à obtenir. */
class IgdbUnavailableException(message: String) : Exception(message)

/**
 * Enchaîne les appels IGDB nécessaires à la recherche et à la lecture d'un jeu : authentification
 * Twitch, requêtes Apicalypse ([IgdbMappers]) et règles de fusion ([IgdbGameMerge]). Les erreurs
 * (réseau, authentification, quota) remontent en exceptions : c'est le repository appelant qui les
 * traduit en `Resource.Error` ou en `null`.
 *
 * Débit IGDB : 4 requêtes par seconde au plus. Une recherche en fait au plus cinq, à la suite ;
 * un 429 est donc retenté une fois après une courte pause ([query]).
 */
@Singleton
class IgdbGateway @Inject constructor(
    private val igdbApi: IgdbApi,
    private val tokenProvider: IgdbTokenSource,
) {

    /**
     * Recherche par texte : jeux de base filtrés, éditions et portages fusionnés (plateformes
     * unies), triés par pertinence, 20 au plus.
     */
    suspend fun search(text: String): List<MergedGame> {
        val pool = LinkedHashMap<Long, IgdbGameDto>()
        query(buildCatalogSearchQuery(text)).forEach { pool[it.id] = it }

        // Parents manquants : une édition ou un portage trouvé par la recherche peut avoir son jeu
        // de base hors du lot (ex. « Definitive Edition » cherchée seule). Deux niveaux au plus.
        repeat(MAX_MERGE_DEPTH) {
            val missing = pool.values.mapNotNull { it.mergeParentId() }.filter { it !in pool }.distinct()
            if (missing.isEmpty()) return@repeat
            // Un parent d'un type exclu (ex. un bundle) reste hors du lot : le jeu garde sa fiche.
            val parents = query(buildGamesByIdsQuery(missing)).filter { it.isKeptGameType() }
            parents.forEach { pool[it.id] = it }
        }

        val ranked = rankByRelevance(text, buildMergedGames(pool.values))
            .take(MAX_SEARCH_RESULTS)
            .map { it.game }

        // Éditions et portages des jeux retenus, pour que leurs plateformes soient justes : on ne
        // les demande que pour les 20 jeux affichés, pas pour tous les candidats.
        attachDescendants(pool, ranked.map { it.id })

        val merged = buildMergedGames(pool.values).associateBy { it.game.id }
        return ranked.mapNotNull { merged[it.id] }
    }

    /**
     * Relit un jeu par son id, avec les plateformes de ses éditions et portages. `null` si IGDB ne
     * le connaît pas (ou si l'id sort de la borne acceptée : aucune requête n'est alors envoyée).
     */
    suspend fun loadById(igdbId: Long): MergedGame? {
        val validId = igdbIdOrNull(igdbId) ?: return null
        val game = query(buildGameByIdQuery(validId)).firstOrNull { it.id == validId } ?: return null
        val pool = linkedMapOf(game.id to game)
        attachDescendants(pool, listOf(game.id))
        return buildMergedGames(pool.values).firstOrNull { it.game.id == game.id }
    }

    /**
     * Remonte d'une édition ou d'un portage à son jeu de base (au plus [MAX_MERGE_DEPTH] niveaux,
     * une requête par niveau). Si un parent est introuvable, on s'arrête au dernier jeu connu.
     */
    suspend fun resolveBase(game: IgdbGameDto): IgdbGameDto {
        var current = game
        repeat(MAX_MERGE_DEPTH) {
            val parentId = current.mergeParentId()?.let(::igdbIdOrNull) ?: return current
            current = query(buildGameByIdQuery(parentId)).firstOrNull { it.id == parentId } ?: return current
        }
        return current
    }

    /** Candidats pour la correspondance par titre d'un jeu sans id IGDB (voir `findBestMatch`). */
    suspend fun searchByTitle(title: String): List<IgdbGameDto> = query(buildSearchQuery(title))

    /** Durées de jeu (en secondes) d'un jeu IGDB, ou `null` si IGDB n'en a pas. */
    suspend fun timeToBeat(igdbId: Long): IgdbTimeToBeatDto? {
        val validId = igdbIdOrNull(igdbId) ?: return null
        val (clientId, authorization) = credentials()
        return withRateLimitRetry {
            igdbApi.getTimeToBeat(clientId, authorization, buildTimeToBeatQuery(validId).toApicalypseBody())
        }.firstOrNull()
    }

    /** Ajoute au [pool] les éditions et portages des [rootIds], sur [MAX_MERGE_DEPTH] niveaux. */
    private suspend fun attachDescendants(pool: MutableMap<Long, IgdbGameDto>, rootIds: List<Long>) {
        var frontier = rootIds
        repeat(MAX_MERGE_DEPTH) {
            if (frontier.isEmpty()) return
            val children = query(buildChildrenQuery(frontier))
                .filter { it.isKeptGameType() && it.mergeParentId() in frontier }
            // Un enfant déjà dans le lot (trouvé par la recherche) reste à explorer au niveau suivant.
            children.forEach { pool.putIfAbsent(it.id, it) }
            frontier = children.map { it.id }
        }
    }

    private suspend fun query(apicalypse: String): List<IgdbGameDto> {
        val (clientId, authorization) = credentials()
        // Un jeu sans nom ne peut ni s'afficher ni se rapprocher d'un titre : il est écarté, sans
        // faire échouer le reste de la réponse (voir `IgdbGameDto`).
        return withRateLimitRetry { igdbApi.searchGames(clientId, authorization, apicalypse.toApicalypseBody()) }
            .filter { !it.name.isNullOrBlank() }
    }

    private suspend fun credentials(): Pair<String, String> {
        val token = tokenProvider.getValidToken() ?: throw IgdbUnavailableException("jeton IGDB indisponible")
        return BuildConfig.IGDB_CLIENT_ID to "Bearer $token"
    }

    /** Un 429 (débit dépassé) est retenté une fois après une pause ; toute autre erreur remonte telle quelle. */
    private suspend fun <T> withRateLimitRetry(call: suspend () -> T): T =
        try {
            call()
        } catch (e: HttpException) {
            if (e.code() != HTTP_TOO_MANY_REQUESTS) throw e
            delay(RATE_LIMIT_PAUSE_MILLIS)
            call()
        }

    private fun String.toApicalypseBody() = toRequestBody(APICALYPSE_MEDIA_TYPE)

    private companion object {
        val APICALYPSE_MEDIA_TYPE = "text/plain".toMediaType()
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val RATE_LIMIT_PAUSE_MILLIS = 1_000L
    }
}
