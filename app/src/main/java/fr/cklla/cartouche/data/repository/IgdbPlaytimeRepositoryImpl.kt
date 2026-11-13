package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.remote.igdb.IgdbGateway
import fr.cklla.cartouche.data.remote.igdb.buildCoverUrl
import fr.cklla.cartouche.data.remote.igdb.findBestMatch
import fr.cklla.cartouche.data.remote.igdb.formatIgdbPlatforms
import fr.cklla.cartouche.data.remote.igdb.igdbReleaseYear
import fr.cklla.cartouche.data.remote.igdb.isCertainMatch
import fr.cklla.cartouche.data.remote.igdb.toEstimatedHours
import fr.cklla.cartouche.domain.model.IgdbGameMatch
import fr.cklla.cartouche.domain.model.IgdbLookup
import fr.cklla.cartouche.domain.model.IgdbPlaytimeEstimate
import fr.cklla.cartouche.domain.model.igdbIdOrNull
import fr.cklla.cartouche.domain.repository.IgdbPlaytimeRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/** Implémentation IGDB du [IgdbPlaytimeRepository]. */
class IgdbPlaytimeRepositoryImpl @Inject constructor(
    private val gateway: IgdbGateway,
) : IgdbPlaytimeRepository {

    override suspend fun findEstimatedPlaytime(title: String, releaseYear: Int?, igdbId: Long?): IgdbLookup =
        try {
            val knownId = igdbIdOrNull(igdbId)
            val match = if (knownId != null) readGame(knownId, certain = true) else matchByName(title, releaseYear)
            if (match == null) IgdbLookup.NotFound else IgdbLookup.Found(match)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Réseau, authentification, quota : pas une réponse d'IGDB, à retenter plus tard.
            IgdbLookup.Failed
        }

    /**
     * Jeu sans id IGDB : correspondance par nom et année, puis remontée au jeu de base. Si le
     * rapprochement est **certain** (voir `isCertainMatch`), on lit le jeu de base par id et on
     * en retient l'id et la jaquette. Sinon on ne lit que les plateformes et les durées du
     * candidat, comme avant : le jeu reste sans id, pour qu'une remise à niveau ultérieure puisse
     * le relier correctement.
     */
    private suspend fun matchByName(title: String, releaseYear: Int?): IgdbGameMatch? {
        val candidate = findBestMatch(title, releaseYear, gateway.searchByTitle(title)) ?: return null
        val base = gateway.resolveBase(candidate)
        return if (isCertainMatch(title, releaseYear, base)) {
            readGame(base.id, certain = true)
        } else {
            readGame(candidate.id, certain = false)
        }
    }

    private suspend fun readGame(igdbId: Long, certain: Boolean): IgdbGameMatch? {
        val merged = gateway.loadById(igdbId) ?: return null
        val timeToBeat = gateway.timeToBeat(igdbId)
        return IgdbGameMatch(
            igdbId = merged.game.id.takeIf { certain },
            platform = formatIgdbPlatforms(merged.platforms),
            coverUrl = buildCoverUrl(merged.game.cover?.imageId).takeIf { certain },
            releaseYear = igdbReleaseYear(merged.game).takeIf { certain },
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
