package fr.cklla.cartouche.data.remote.firestore

import fr.cklla.cartouche.domain.model.Game
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * Faux data source Firestore en mémoire, utilisé pour tester [fr.cklla.cartouche.data.repository.GameRepositoryImpl]
 * sans SDK Firebase réel. Un seul utilisateur simulé à la fois (pas besoin de plus pour ces tests) :
 * `uid` est ignoré, tout passe par [remoteGames].
 */
class FakeFirestoreGameDataSource : FirestoreGameDataSource {

    val remoteGames = MutableStateFlow<List<Game>>(emptyList())

    /** Permet de simuler un échec réseau/Firestore dans les tests. */
    var shouldThrowOnWrite = false

    /**
     * Nombre d'erreurs que [observeGames] lève avant de se comporter normalement : permet de
     * vérifier que le repository réessaye au lieu d'abandonner la synchro définitivement.
     */
    var failedObserveAttempts = 0

    val upsertedGames = mutableListOf<Game>()
    val deletedGameIds = mutableListOf<String>()
    var uploadAllCallCount = 0
        private set
    var clearLocalCacheCallCount = 0
        private set
    var observeCallCount = 0
        private set

    override fun observeGames(uid: String): Flow<List<Game>> = flow {
        observeCallCount++
        if (observeCallCount <= failedObserveAttempts) {
            error("Échec Firestore simulé à l'écoute")
        }
        emitAll(remoteGames)
    }

    override suspend fun fetchGamesOnce(uid: String): List<Game> = remoteGames.value

    override suspend fun upsertGame(uid: String, game: Game) {
        if (shouldThrowOnWrite) error("Échec Firestore simulé")
        upsertedGames += game
        remoteGames.update { list -> list.filterNot { it.id == game.id } + game }
    }

    override suspend fun deleteGame(uid: String, gameId: String) {
        if (shouldThrowOnWrite) error("Échec Firestore simulé")
        deletedGameIds += gameId
        remoteGames.update { list -> list.filterNot { it.id == gameId } }
    }

    override suspend fun uploadAll(uid: String, games: List<Game>) {
        if (shouldThrowOnWrite) error("Échec Firestore simulé")
        uploadAllCallCount++
        remoteGames.update { it + games }
    }

    override suspend fun clearLocalCache() {
        clearLocalCacheCallCount++
    }
}
