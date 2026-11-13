package fr.cklla.cartouche.ui.detail

import fr.cklla.cartouche.domain.model.IgdbGameMatch
import fr.cklla.cartouche.domain.repository.IgdbPlaytimeRepository

/** Double de test en mémoire : renvoie un résultat fixe et compte les appels reçus. */
class FakeIgdbPlaytimeRepository(private val result: IgdbGameMatch? = null) : IgdbPlaytimeRepository {

    var callCount = 0
        private set

    /** Id IGDB reçu au dernier appel, pour vérifier qu'un id connu est bien transmis. */
    var lastIgdbId: Long? = null
        private set

    override suspend fun findEstimatedPlaytime(title: String, releaseYear: Int?, igdbId: Long?): IgdbGameMatch? {
        callCount++
        lastIgdbId = igdbId
        return result
    }
}
