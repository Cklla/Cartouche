package fr.cklla.cartouche.ui.detail

import fr.cklla.cartouche.domain.model.IgdbGameMatch
import fr.cklla.cartouche.domain.repository.IgdbPlaytimeRepository

/** Double de test en mémoire : renvoie un résultat fixe et compte les appels reçus. */
class FakeIgdbPlaytimeRepository(private val result: IgdbGameMatch? = null) : IgdbPlaytimeRepository {

    var callCount = 0
        private set

    override suspend fun findEstimatedPlaytime(title: String, releaseYear: Int?, rawgId: Long?): IgdbGameMatch? {
        callCount++
        return result
    }
}
