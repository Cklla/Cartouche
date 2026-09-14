package fr.cklla.cartouche.ui.recherche

import fr.cklla.cartouche.domain.model.GameSearchResult
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.repository.GameSearchRepository

/** Faux repository de recherche, pour tester [RechercheViewModel] sans appel RAWG réel. */
class FakeGameSearchRepository : GameSearchRepository {

    var response: Resource<List<GameSearchResult>> = Resource.Success(emptyList())
    var searchCallCount = 0
        private set

    override suspend fun searchGames(query: String): Resource<List<GameSearchResult>> {
        searchCallCount++
        return response
    }
}
