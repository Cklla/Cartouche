package fr.cklla.cartouche.data.remote.firestore

import fr.cklla.cartouche.domain.model.Game
import kotlinx.coroutines.flow.Flow

/**
 * Accès à la collection Firestore d'un utilisateur, sur le modèle métier [Game] plutôt que sur des
 * types Firestore bruts. Interface séparée de son implémentation (contrairement à `GameMappers`,
 * simples fonctions) pour pouvoir la remplacer par un fake en test unitaire — `FirebaseFirestore`
 * est une classe du SDK Android, impossible à instancier dans un test JVM sans Robolectric/mock.
 *
 * Chemin Firestore : `users/{uid}/games/{gameId}` — chaque utilisateur ne voit que sa propre
 * collection (voir `firestore.rules`).
 */
interface FirestoreGameDataSource {

    /** Écoute temps réel de la collection de l'utilisateur [uid]. */
    fun observeGames(uid: String): Flow<List<Game>>

    /** Lecture ponctuelle (pas d'écoute), utilisée pour le bootstrap au premier lancement. */
    suspend fun fetchGamesOnce(uid: String): List<Game>

    suspend fun upsertGame(uid: String, game: Game)

    suspend fun deleteGame(uid: String, gameId: String)

    /** Écriture groupée, utilisée pour l'upload initial du backlog local pré-existant. */
    suspend fun uploadAll(uid: String, games: List<Game>)
}
