package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.remote.igdb.IgdbGateway
import fr.cklla.cartouche.data.remote.igdb.buildCoverUrl
import fr.cklla.cartouche.data.remote.igdb.findBestMatch
import fr.cklla.cartouche.data.remote.igdb.formatIgdbPlatforms
import fr.cklla.cartouche.data.remote.igdb.igdbReleaseYear
import fr.cklla.cartouche.data.remote.igdb.toEstimatedHours
import fr.cklla.cartouche.domain.model.IgdbGameMatch
import fr.cklla.cartouche.domain.model.IgdbPlaytimeEstimate
import fr.cklla.cartouche.domain.model.igdbIdOrNull
import fr.cklla.cartouche.domain.repository.IgdbPlaytimeRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/** Implémentation IGDB du [IgdbPlaytimeRepository]. */
class IgdbPlaytimeRepositoryImpl @Inject constructor(
    private val gateway: IgdbGateway,
) : IgdbPlaytimeRepository {

    override suspend fun findEstimatedPlaytime(title: String, releaseYear: Int?, igdbId: Long?): IgdbGameMatch? =
        try {
            // Id connu : lecture directe. Sinon : correspondance par nom et année, puis lecture de
            // l'id retenu (mêmes données dans les deux cas).
            val resolvedId = igdbIdOrNull(igdbId)
                ?: gateway.searchByTitle(title).let { findBestMatch(title, releaseYear, it) }?.id
            resolvedId?.let { readGame(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private suspend fun readGame(igdbId: Long): IgdbGameMatch? {
        val merged = gateway.loadById(igdbId) ?: return null
        val timeToBeat = gateway.timeToBeat(igdbId)
        return IgdbGameMatch(
            igdbId = merged.game.id,
            platform = formatIgdbPlatforms(merged.platforms),
            coverUrl = buildCoverUrl(merged.game.cover?.imageId),
            releaseYear = igdbReleaseYear(merged.game),
            playtimeEstimate = timeToBeat?.let {
                IgdbPlaytimeEstimate(
                    hastilyHours = toEstimatedHours(it.hastilySeconds),
                    normallyHours = toEstimatedHours(it.normallySeconds),
                    completelyHours = toEstimatedHours(it.completelySeconds),
                )
            },
        )
    }
}
