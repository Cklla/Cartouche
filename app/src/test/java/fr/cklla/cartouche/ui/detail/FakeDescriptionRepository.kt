package fr.cklla.cartouche.ui.detail

import fr.cklla.cartouche.domain.description.DescriptionView
import fr.cklla.cartouche.domain.repository.DescriptionRepository

/**
 * Double de test du repository des descriptions. Les lectures seules ([peeks], réponse [peeker]) et les
 * demandes de traduction ([calls], réponse [responder]) sont comptées séparément : seules les secondes
 * coûtent quelque chose. Les deux réponses peuvent suspendre pour simuler une réponse en retard.
 */
class FakeDescriptionRepository(
    var peeker: suspend (gameId: String, igdbId: Long?) -> DescriptionView = { _, _ -> DescriptionView.Collapsed },
    var responder: suspend (gameId: String, igdbId: Long?) -> DescriptionView = { _, _ -> DescriptionView.None },
) : DescriptionRepository {

    val peeks = mutableListOf<Pair<String, Long?>>()
    val calls = mutableListOf<Pair<String, Long?>>()

    override suspend fun peek(gameId: String, igdbId: Long?): DescriptionView {
        peeks += gameId to igdbId
        return peeker(gameId, igdbId)
    }

    override suspend fun resolve(gameId: String, igdbId: Long?): DescriptionView {
        calls += gameId to igdbId
        return responder(gameId, igdbId)
    }
}
