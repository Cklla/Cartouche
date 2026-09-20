package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.BuildConfig
import fr.cklla.cartouche.data.remote.RawgApi
import fr.cklla.cartouche.data.remote.extractSteamAppId
import fr.cklla.cartouche.data.remote.igdb.IgdbApi
import fr.cklla.cartouche.data.remote.igdb.IgdbTokenProvider
import fr.cklla.cartouche.data.remote.igdb.buildPlatformsQuery
import fr.cklla.cartouche.data.remote.igdb.buildSearchQuery
import fr.cklla.cartouche.data.remote.igdb.buildSteamExternalGameQuery
import fr.cklla.cartouche.data.remote.igdb.buildTimeToBeatQuery
import fr.cklla.cartouche.data.remote.igdb.findBestMatch
import fr.cklla.cartouche.data.remote.igdb.formatIgdbPlatforms
import fr.cklla.cartouche.data.remote.igdb.toEstimatedHours
import fr.cklla.cartouche.domain.model.IgdbGameMatch
import fr.cklla.cartouche.domain.model.IgdbPlaytimeEstimate
import fr.cklla.cartouche.domain.repository.IgdbPlaytimeRepository
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Implémentation IGDB du [IgdbPlaytimeRepository]. */
class IgdbPlaytimeRepositoryImpl @Inject constructor(
    private val igdbApi: IgdbApi,
    private val rawgApi: RawgApi,
    private val tokenProvider: IgdbTokenProvider,
) : IgdbPlaytimeRepository {

    override suspend fun findEstimatedPlaytime(title: String, releaseYear: Int?, rawgId: Long?): IgdbGameMatch? {
        val token = tokenProvider.getValidToken() ?: return null
        val authorization = "Bearer $token"

        return runCatching {
            val (igdbGameId, platform) = resolveIgdbGame(title, releaseYear, rawgId, authorization)
                ?: return@runCatching null

            val timeToBeat = igdbApi.getTimeToBeat(
                clientId = BuildConfig.IGDB_CLIENT_ID,
                authorization = authorization,
                query = buildTimeToBeatQuery(igdbGameId).toApicalypseBody(),
            ).firstOrNull()

            val playtimeEstimate = timeToBeat?.let {
                IgdbPlaytimeEstimate(
                    hastilyHours = toEstimatedHours(it.hastilySeconds),
                    normallyHours = toEstimatedHours(it.normallySeconds),
                    completelyHours = toEstimatedHours(it.completelySeconds),
                )
            }

            // L'id IGDB a pu être résolu sans qu'aucune des deux informations qui en dépendent ne
            // soit disponible (pas de `game_time_to_beats`, pas de plateforme renseignée) : rien
            // d'exploitable à remonter, traité comme un échec de correspondance.
            if (playtimeEstimate == null && platform == null) return@runCatching null

            IgdbGameMatch(playtimeEstimate = playtimeEstimate, platform = platform)
        }.getOrNull()
    }

    /**
     * Résout l'id IGDB du jeu (cascade ID Steam → nom+année, voir en tête de `IgdbMappers`) et, si
     * elle est disponible, sa liste de plateformes déjà formatée comme `Game.platform` (voir
     * `IgdbMappers.formatIgdbPlatforms`) — `null` si IGDB n'a aucune plateforme pour ce jeu, jamais
     * une chaîne vide.
     */
    private suspend fun resolveIgdbGame(
        title: String,
        releaseYear: Int?,
        rawgId: Long?,
        authorization: String,
    ): Pair<Long, String?>? {
        val steamGameId = findGameIdBySteamAppId(rawgId, authorization)
        if (steamGameId != null) {
            return steamGameId to findPlatformsById(steamGameId, authorization)
        }

        val match = findGameByNameAndYear(title, releaseYear, authorization) ?: return null
        return match.id to formatIgdbPlatforms(match.platforms)
    }

    /**
     * Étape 1 de la cascade (priorité la plus haute, voir `IgdbMappers`) : si RAWG connaît une
     * fiche Steam pour ce jeu, on retrouve directement le jeu IGDB par son App ID Steam — signal
     * fiable à 100%, pas besoin de vérifier le nom ensuite.
     *
     * L'appel RAWG (`getStoreLinks`) est protégé par son propre `runCatching` : un échec réseau
     * sur cette étape optionnelle ne doit pas empêcher de retomber sur la correspondance par nom
     * (étape 2), qui reste possible indépendamment des liens boutiques RAWG.
     */
    private suspend fun findGameIdBySteamAppId(rawgId: Long?, authorization: String): Long? {
        val steamAppId = rawgId
            ?.let { runCatching { rawgApi.getStoreLinks(rawgId = it, apiKey = BuildConfig.RAWG_API_KEY) }.getOrNull() }
            ?.let { extractSteamAppId(it.results) }
            ?: return null

        return igdbApi.findExternalGame(
            clientId = BuildConfig.IGDB_CLIENT_ID,
            authorization = authorization,
            query = buildSteamExternalGameQuery(steamAppId).toApicalypseBody(),
        ).firstOrNull()?.gameId
    }

    /** Étape 2 de la cascade : correspondance par nom normalisé, départagée par année de sortie. */
    private suspend fun findGameByNameAndYear(title: String, releaseYear: Int?, authorization: String) =
        igdbApi.searchGames(
            clientId = BuildConfig.IGDB_CLIENT_ID,
            authorization = authorization,
            query = buildSearchQuery(title).toApicalypseBody(),
        ).let { candidates -> findBestMatch(title, releaseYear, candidates) }

    /**
     * Appel de suivi minimal pour la branche ID Steam de la cascade : `external_games` ne renvoie
     * que l'id du jeu (voir [findGameIdBySteamAppId]), les plateformes n'y sont pas disponibles.
     * Réutilise l'endpoint `games` (comme [findGameByNameAndYear]) avec une requête ciblée par id
     * plutôt qu'un nouvel endpoint dédié.
     */
    private suspend fun findPlatformsById(igdbGameId: Long, authorization: String): String? =
        igdbApi.searchGames(
            clientId = BuildConfig.IGDB_CLIENT_ID,
            authorization = authorization,
            query = buildPlatformsQuery(igdbGameId).toApicalypseBody(),
        ).firstOrNull()?.let { formatIgdbPlatforms(it.platforms) }

    private fun String.toApicalypseBody() = toRequestBody(APICALYPSE_MEDIA_TYPE)

    private companion object {
        val APICALYPSE_MEDIA_TYPE = "text/plain".toMediaType()
    }
}
