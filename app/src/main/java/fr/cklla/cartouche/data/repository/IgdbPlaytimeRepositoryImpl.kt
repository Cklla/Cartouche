package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.BuildConfig
import fr.cklla.cartouche.data.remote.RawgApi
import fr.cklla.cartouche.data.remote.extractSteamAppId
import fr.cklla.cartouche.data.remote.igdb.IgdbApi
import fr.cklla.cartouche.data.remote.igdb.IgdbTokenProvider
import fr.cklla.cartouche.data.remote.igdb.buildSearchQuery
import fr.cklla.cartouche.data.remote.igdb.buildSteamExternalGameQuery
import fr.cklla.cartouche.data.remote.igdb.buildTimeToBeatQuery
import fr.cklla.cartouche.data.remote.igdb.findBestMatch
import fr.cklla.cartouche.data.remote.igdb.toEstimatedHours
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

    override suspend fun findEstimatedPlaytime(title: String, releaseYear: Int?, rawgId: Long?): IgdbPlaytimeEstimate? {
        val token = tokenProvider.getValidToken() ?: return null
        val authorization = "Bearer $token"

        return runCatching {
            val igdbGameId = findGameIdBySteamAppId(rawgId, authorization)
                ?: findGameIdByNameAndYear(title, releaseYear, authorization)
                ?: return@runCatching null

            val timeToBeat = igdbApi.getTimeToBeat(
                clientId = BuildConfig.IGDB_CLIENT_ID,
                authorization = authorization,
                query = buildTimeToBeatQuery(igdbGameId).toApicalypseBody(),
            ).firstOrNull() ?: return@runCatching null

            IgdbPlaytimeEstimate(
                hastilyHours = toEstimatedHours(timeToBeat.hastilySeconds),
                normallyHours = toEstimatedHours(timeToBeat.normallySeconds),
                completelyHours = toEstimatedHours(timeToBeat.completelySeconds),
            )
        }.getOrNull()
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
    private suspend fun findGameIdByNameAndYear(title: String, releaseYear: Int?, authorization: String): Long? {
        val candidates = igdbApi.searchGames(
            clientId = BuildConfig.IGDB_CLIENT_ID,
            authorization = authorization,
            query = buildSearchQuery(title).toApicalypseBody(),
        )
        return findBestMatch(title, releaseYear, candidates)?.id
    }

    private fun String.toApicalypseBody() = toRequestBody(APICALYPSE_MEDIA_TYPE)

    private companion object {
        val APICALYPSE_MEDIA_TYPE = "text/plain".toMediaType()
    }
}
