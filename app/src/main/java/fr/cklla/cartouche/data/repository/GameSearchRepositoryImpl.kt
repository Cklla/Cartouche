package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.BuildConfig
import fr.cklla.cartouche.data.remote.RawgApi
import fr.cklla.cartouche.data.remote.toDomain
import fr.cklla.cartouche.domain.model.GameSearchResult
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.repository.GameSearchRepository
import javax.inject.Inject

/** Implémentation RAWG du [GameSearchRepository]. */
class GameSearchRepositoryImpl @Inject constructor(
    private val rawgApi: RawgApi,
) : GameSearchRepository {

    override suspend fun searchGames(query: String): Resource<List<GameSearchResult>> {
        if (query.isBlank()) return Resource.Success(emptyList())

        return runCatching {
            rawgApi.searchGames(apiKey = BuildConfig.RAWG_API_KEY, query = query).results.map { it.toDomain() }
        }.fold(
            onSuccess = { Resource.Success(it) },
            onFailure = { Resource.Error("Impossible de contacter RAWG. Vérifie ta connexion.", it) },
        )
    }
}
