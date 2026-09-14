package fr.cklla.cartouche.data.remote.firestore

import com.google.firebase.firestore.FirebaseFirestore
import fr.cklla.cartouche.domain.model.Game
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Implémentation [FirestoreGameDataSource] adossée au SDK `FirebaseFirestore`. */
class FirestoreGameDataSourceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : FirestoreGameDataSource {

    private fun gamesCollection(uid: String) =
        firestore.collection("users").document(uid).collection("games")

    // `callbackFlow` + `awaitClose` : le listener Firestore est annulé proprement dès que le
    // collecteur (côté `GameRepositoryImpl`, via `collectLatest` sur l'utilisateur courant) se
    // désabonne, sans quoi il continuerait à tourner pour un utilisateur qui s'est déconnecté.
    override fun observeGames(uid: String): Flow<List<Game>> = callbackFlow {
        val registration = gamesCollection(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val games = snapshot?.documents.orEmpty().mapNotNull { doc ->
                doc.data?.let { mapToGame(doc.id, it) }
            }
            trySend(games)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun fetchGamesOnce(uid: String): List<Game> =
        gamesCollection(uid).get().await().documents.mapNotNull { doc ->
            doc.data?.let { mapToGame(doc.id, it) }
        }

    override suspend fun upsertGame(uid: String, game: Game) {
        gamesCollection(uid).document(game.id).set(game.toFirestoreMap()).await()
    }

    override suspend fun deleteGame(uid: String, gameId: String) {
        gamesCollection(uid).document(gameId).delete().await()
    }

    // Écriture groupée pour le bootstrap (upload du backlog local pré-existant à la première
    // connexion) : un seul batch plutôt qu'un `upsertGame` par jeu, atomique côté Firestore.
    override suspend fun uploadAll(uid: String, games: List<Game>) {
        if (games.isEmpty()) return
        val collection = gamesCollection(uid)
        val batch = firestore.batch()
        games.forEach { game -> batch.set(collection.document(game.id), game.toFirestoreMap()) }
        batch.commit().await()
    }
}
