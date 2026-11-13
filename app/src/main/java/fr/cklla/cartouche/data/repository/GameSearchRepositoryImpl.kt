package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.remote.igdb.IgdbGateway
import fr.cklla.cartouche.data.remote.igdb.toSearchResult
import fr.cklla.cartouche.domain.model.GameSearchResult
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.repository.GameSearchRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/** Implémentation IGDB du [GameSearchRepository]. */
class GameSearchRepositoryImpl @Inject constructor(
    private val gateway: IgdbGateway,
) : GameSearchRepository {

    override suspend fun searchGames(query: String): Resource<List<GameSearchResult>> {
        if (query.isBlank()) return Resource.Success(emptyList())

        return try {
            Resource.Success(gateway.search(query.trim()).mapNotNull { it.toSearchResult() })
        } catch (e: CancellationException) {
            // Une recherche remplacée par la frappe suivante est annulée : ce n'est pas une erreur.
            throw e
        } catch (e: Exception) {
            Resource.Error("Recherche IGDB impossible", e)
        }
    }
}
