package fr.cklla.cartouche.ui.detail

import fr.cklla.cartouche.domain.model.IgdbGameMatch
import fr.cklla.cartouche.domain.model.IgdbLookup
import fr.cklla.cartouche.domain.repository.IgdbPlaytimeRepository

/**
 * Double de test en mémoire : renvoie un résultat fixe et compte les appels reçus. [result] `null`
 * simule « introuvable » ; [fails] simule une lecture en échec (réseau, quota, jeton).
 */
class FakeIgdbPlaytimeRepository(
    private val result: IgdbGameMatch? = null,
    var fails: Boolean = false,
) : IgdbPlaytimeRepository {

    var callCount = 0
        private set

    /** Id IGDB reçu au dernier appel, pour vérifier qu'un id connu est bien transmis. */
    var lastIgdbId: Long? = null
        private set

    override suspend fun findEstimatedPlaytime(title: String, releaseYear: Int?, igdbId: Long?): IgdbLookup {
        callCount++
        lastIgdbId = igdbId
        return when {
            fails -> IgdbLookup.Failed
            result == null -> IgdbLookup.NotFound
            else -> IgdbLookup.Found(result)
        }
    }
}
