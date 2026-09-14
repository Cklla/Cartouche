package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.BuildConfig
import fr.cklla.cartouche.data.remote.igdb.IgdbApi
import fr.cklla.cartouche.data.remote.igdb.IgdbTokenProvider
import fr.cklla.cartouche.data.remote.igdb.buildSearchQuery
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
    private val tokenProvider: IgdbTokenProvider,
) : IgdbPlaytimeRepository {

    override suspend fun findEstimatedPlaytime(title: String): IgdbPlaytimeEstimate? {
        val token = tokenProvider.getValidToken() ?: return null
        val authorization = "Bearer $token"

        return runCatching {
            val candidates = igdbApi.searchGames(
                clientId = BuildConfig.IGDB_CLIENT_ID,
                authorization = authorization,
                query = buildSearchQuery(title).toApicalypseBody(),
            )
            val match = findBestMatch(title, candidates) ?: return@runCatching null

            val timeToBeat = igdbApi.getTimeToBeat(
                clientId = BuildConfig.IGDB_CLIENT_ID,
                authorization = authorization,
                query = buildTimeToBeatQuery(match.id).toApicalypseBody(),
            ).firstOrNull() ?: return@runCatching null

            IgdbPlaytimeEstimate(
                hastilyHours = toEstimatedHours(timeToBeat.hastilySeconds),
                normallyHours = toEstimatedHours(timeToBeat.normallySeconds),
                completelyHours = toEstimatedHours(timeToBeat.completelySeconds),
            )
        }.getOrNull()
    }

    private fun String.toApicalypseBody() = toRequestBody(APICALYPSE_MEDIA_TYPE)

    private companion object {
        val APICALYPSE_MEDIA_TYPE = "text/plain".toMediaType()
    }
}
