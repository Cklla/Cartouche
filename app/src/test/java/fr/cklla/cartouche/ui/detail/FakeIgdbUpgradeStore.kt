package fr.cklla.cartouche.ui.detail

import fr.cklla.cartouche.domain.repository.IgdbUpgradeStore

/** Double de test en mémoire du magasin de remise à niveau IGDB. */
class FakeIgdbUpgradeStore(done: Set<String> = emptySet()) : IgdbUpgradeStore {

    val doneIds = done.toMutableSet()

    override fun isDone(gameId: String): Boolean = gameId in doneIds

    override fun markDone(gameId: String) {
        doneIds += gameId
    }
}
