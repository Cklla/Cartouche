package fr.cklla.cartouche.domain.repository

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Point d'accès unique aux données du backlog pour les ViewModels.
 *
 * Le ViewModel ne connaît que cette interface : il ignore si les jeux
 * viennent de Room, de Firestore ou d'un cache mémoire. L'implémentation
 * (voir `data.repository.GameRepositoryImpl`) orchestre Room (lecture/écriture
 * hors-ligne) et Firestore (synchro multi-appareils).
 */
interface GameRepository {

    /** Flux de la totalité du backlog, mis à jour automatiquement à chaque changement local. */
    fun observeGames(): Flow<List<Game>>

    /** Flux d'un jeu précis, ou `null` s'il n'existe pas (ou plus). */
    fun observeGame(id: String): Flow<Game?>

    /** Ajoute un nouveau jeu au backlog. Retourne l'id (UUID) généré en cas de succès. */
    suspend fun addGame(game: Game): Resource<String>

    /** Met à jour un jeu existant (statut, note, temps de jeu, notes libres...). */
    suspend fun updateGame(game: Game): Resource<Unit>

    /**
     * Classe un jeu dans l'année [year] : année de fin s'il est Terminé (`completedAt`), année
     * d'abandon s'il est Abandonné (`abandonedAt`). N'agit que si le jeu existe, est dans le
     * backlog et porte l'un de ces deux statuts, et si l'année est permise (ni future, ni antérieure
     * à la sortie du jeu) ; sinon, ou si la valeur ne change pas, c'est un no-op qui renvoie
     * `Success`. Indépendant de tout écran : une future sélection multiple pourra l'appeler par jeu.
     */
    suspend fun setStatusYear(gameId: String, year: Int): Resource<Unit>

    /** Retire un jeu du backlog. */
    suspend fun deleteGame(id: String): Resource<Unit>
}
