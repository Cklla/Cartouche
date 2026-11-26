package fr.cklla.cartouche.ui.detail

import fr.cklla.cartouche.domain.description.DescriptionView
import fr.cklla.cartouche.domain.repository.DescriptionRepository

/**
 * Double de test du repository des descriptions : enregistre chaque demande `(gameId, igdbId)` et
 * répond par [responder] (qui peut suspendre pour simuler une réponse en retard).
 */
class FakeDescriptionRepository(
    var responder: suspend (gameId: String, igdbId: Long?) -> DescriptionView = { _, _ -> DescriptionView.None },
) : DescriptionRepository {

    val calls = mutableListOf<Pair<String, Long?>>()

    override suspend fun resolve(gameId: String, igdbId: Long?): DescriptionView {
        calls += gameId to igdbId
        return responder(gameId, igdbId)
    }
}
