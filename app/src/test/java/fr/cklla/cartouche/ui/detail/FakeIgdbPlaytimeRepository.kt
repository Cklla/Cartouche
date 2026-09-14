package fr.cklla.cartouche.ui.detail

import fr.cklla.cartouche.domain.repository.IgdbPlaytimeRepository

/** Double de test en mémoire : renvoie une valeur fixe et compte les appels reçus. */
class FakeIgdbPlaytimeRepository(private val result: Int? = null) : IgdbPlaytimeRepository {

    var callCount = 0
        private set

    override suspend fun findEstimatedPlaytimeHours(title: String): Int? {
        callCount++
        return result
    }
}
