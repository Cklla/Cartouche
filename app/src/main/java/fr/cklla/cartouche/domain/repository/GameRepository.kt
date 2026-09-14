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
 * hors-ligne) et, à terme, Firestore (synchro multi-appareils).
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

    /** Retire un jeu du backlog. */
    suspend fun deleteGame(id: String): Resource<Unit>
}
