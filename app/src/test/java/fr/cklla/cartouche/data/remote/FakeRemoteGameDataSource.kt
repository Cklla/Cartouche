package fr.cklla.cartouche.data.remote

import fr.cklla.cartouche.domain.model.Game
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Faux serveur en mémoire, utilisé pour tester la synchro sans SDK réseau. Un seul compte simulé. */
class FakeRemoteGameDataSource : RemoteGameDataSource {

    val games = MutableStateFlow<List<Game>>(emptyList())

    /** Coupure réseau : toute opération lève une [IOException]. */
    var offline = false

    /** Exception levée par chaque écriture (ex. un [PermanentRemoteException]), tant qu'elle est posée. */
    var writeFailure: Throwable? = null

    /** Exception levée par les écritures portant sur un jeu précis (par identifiant), les autres passent. */
    var writeFailureFor: Map<String, Throwable> = emptyMap()

    /** Nombre d'écritures acceptées avant que le réseau ne tombe ; `null` : pas de limite. */
    var writesBeforeFailure: Int? = null

    /** Nombre d'écoutes qui échouent avant de se comporter normalement. */
    var failedChangesAttempts = 0

    /** Écoute qui ne s'établit jamais : ni signal, ni erreur. */
    var changesNeverSubscribe = false

    /** Exécuté après la lecture de l'état mais avant de le renvoyer : simule un envoi pendant la lecture. */
    var onFetch: (suspend () -> Unit)? = null

    var changesCallCount = 0
        private set
    var fetchCount = 0
        private set

    val upsertedGames = mutableListOf<Game>()
    val deletedGameIds = mutableListOf<String>()

    /** Ordre global des écritures reçues. */
    val writeLog = mutableListOf<String>()

    override fun changes(): Flow<Unit> = flow {
        changesCallCount++
        if (changesCallCount <= failedChangesAttempts) error("Échec d'écoute simulé")
        if (changesNeverSubscribe) awaitCancellation()
        checkOnline()
        emit(Unit)
        emitAll(games.drop(1).map { })
    }

    override suspend fun fetchAll(): List<Game> {
        checkOnline()
        fetchCount++
        val snapshot = games.value
        onFetch?.invoke()
        return snapshot
    }

    override suspend fun upsertGame(game: Game) {
        checkWritable()
        writeFailureFor[game.id]?.let { throw it }
        upsertedGames += game
        writeLog += "upsert:${game.id}"
        games.update { list -> list.filterNot { it.id == game.id } + game }
    }

    override suspend fun deleteGame(gameId: String) {
        checkWritable()
        writeFailureFor[gameId]?.let { throw it }
        deletedGameIds += gameId
        writeLog += "delete:$gameId"
        games.update { list -> list.filterNot { it.id == gameId } }
    }

    private fun checkOnline() {
        if (offline) throw IOException("Réseau coupé (simulé)")
    }

    private fun checkWritable() {
        checkOnline()
        writeFailure?.let { throw it }
        writesBeforeFailure?.let { remaining ->
            if (remaining <= 0) throw IOException("Réseau coupé en cours d'envoi (simulé)")
            writesBeforeFailure = remaining - 1
        }
    }
}
