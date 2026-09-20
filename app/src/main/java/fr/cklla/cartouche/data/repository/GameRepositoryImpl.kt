package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.local.GameDao
import fr.cklla.cartouche.data.local.entity.GameEntity
import fr.cklla.cartouche.data.remote.firestore.FirestoreGameDataSource
import fr.cklla.cartouche.di.ApplicationScope
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.repository.AuthRepository
import fr.cklla.cartouche.domain.repository.GameRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * Implémentation Room + Firestore du [GameRepository].
 *
 * Room reste l'unique source lue par l'UI (`observeGames`/`observeGame`, UX offline-first, pas de
 * flicker si Firestore est temporairement indisponible). Firestore devient la source de vérité dès
 * que la synchro démarre : un listener temps réel mirrore en continu son contenu vers Room, et les
 * écritures locales sont répercutées vers Firestore en best-effort.
 */
class GameRepositoryImpl @Inject constructor(
    private val gameDao: GameDao,
    private val firestoreDataSource: FirestoreGameDataSource,
    private val authRepository: AuthRepository,
    @ApplicationScope private val repositoryScope: CoroutineScope,
) : GameRepository {

    init {
        // `collectLatest` : un changement d'utilisateur (déconnexion, ou reconnexion avec un autre
        // compte) annule proprement l'écoute Firestore précédente avant d'en démarrer une nouvelle.
        repositoryScope.launch {
            authRepository.currentUser.collectLatest { user ->
                if (user == null) {
                    clearLocalData()
                } else {
                    syncWith(user.uid)
                }
            }
        }
    }

    override fun observeGames(): Flow<List<Game>> =
        gameDao.observeAll()
            .map { entities -> entities.map { it.toDomain() } }
            // Un miroir Firestore -> Room réémet souvent une liste identique (écho de l'écriture
            // locale elle-même avant confirmation serveur) : éviter les recompositions inutiles.
            .distinctUntilChanged()

    override fun observeGame(id: String): Flow<Game?> =
        gameDao.observeById(id).map { it?.toDomain() }

    // Le UUID est généré ici, avant la première écriture, plutôt que délégué à Room (qui ne sait
    // pas auto-générer un id texte) : c'est aussi le seul endroit qui écrit à la fois dans Room et
    // dans Firestore, les deux doivent donc partager exactement le même id.
    override suspend fun addGame(game: Game): Resource<String> = runCatching {
        val id = game.id.ifBlank { UUID.randomUUID().toString() }
        val gameWithId = game.copy(id = id, completedAt = resolveCompletedAt(previous = null, newStatus = game.status))
        gameDao.insert(gameWithId.toEntity())
        pushToFirestore { uid -> firestoreDataSource.upsertGame(uid, gameWithId) }
        id
    }.fold(
        onSuccess = { Resource.Success(it) },
        onFailure = { Resource.Error("Impossible d'ajouter le jeu au backlog.", it) },
    )

    override suspend fun updateGame(game: Game): Resource<Unit> = runCatching {
        val previous = gameDao.getByIdOnce(game.id)
        val gameToPersist = game.copy(completedAt = resolveCompletedAt(previous, game.status))
        gameDao.update(gameToPersist.toEntity())
        pushToFirestore { uid -> firestoreDataSource.upsertGame(uid, gameToPersist) }
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de mettre à jour le jeu.", it) },
    )

    // Dérive automatiquement la date de complétion à chaque transition de statut, plutôt que de
    // laisser l'UI la renseigner à la main : un seul point de vérité pour "quand un jeu est-il
    // devenu Terminé", que le changement vienne d'une sélection manuelle sur la fiche Détail ou
    // d'ailleurs à l'avenir.
    //
    // Se base sur l'état déjà persisté en Room ([previous]), jamais sur `game.completedAt` tel que
    // fourni par l'appelant : `DetailViewModel.workingGame` ne relit jamais Room après son
    // chargement initial, un `completedAt` calculé ici et jamais propagé en retour dans cette copie
    // de travail locale serait sinon écrasé par `null` au prochain appel.
    //
    // Horodatage posé une seule fois à l'entrée dans TERMINE (pas de re-timestamp si déjà TERMINE,
    // sinon éditer la note ou le temps de jeu déplacerait la date de complétion à chaque fois),
    // effacé dès que le jeu quitte TERMINE (redevient pertinent le jour où il y repasse).
    private fun resolveCompletedAt(previous: GameEntity?, newStatus: GameStatus): Long? {
        val previousStatus = previous?.status?.let { runCatching { GameStatus.valueOf(it) }.getOrNull() }
        return when {
            newStatus != GameStatus.TERMINE -> null
            previousStatus == GameStatus.TERMINE -> previous?.completedAt
            else -> System.currentTimeMillis()
        }
    }

    override suspend fun deleteGame(id: String): Resource<Unit> = runCatching {
        gameDao.deleteById(id)
        pushToFirestore { uid -> firestoreDataSource.deleteGame(uid, id) }
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de retirer le jeu du backlog.", it) },
    )

    // Ne laisse rien du compte précédent sur l'appareil : la base Room, mais aussi le cache que
    // le SDK Firestore tient de son côté. Enveloppé dans un runCatching parce qu'une exception
    // ici (base verrouillée, purge Firestore refusée) annulerait le collecteur de `currentUser`
    // et couperait la synchro pour tout le reste de la vie du process.
    private suspend fun clearLocalData() {
        runCatching {
            gameDao.clearAll()
            firestoreDataSource.clearLocalCache()
        }
    }

    // Une erreur Firestore (réseau, règles de sécurité, quota) termine le flux d'écoute. Sans le
    // retry ci-dessous, la synchro s'arrêtait définitivement au premier incident, sans que rien
    // ne le signale : l'app continuait à tourner sur le seul contenu de Room, et il fallait la
    // relancer pour qu'elle se resynchronise.
    private suspend fun syncWith(uid: String) {
        bootstrapIfNeeded(uid)
        firestoreDataSource.observeGames(uid)
            .onEach { games -> mirrorIntoRoom(games) }
            .retryWhen { _, attempt ->
                if (attempt >= MAX_SYNC_ATTEMPTS) {
                    false
                } else {
                    // Délai croissant : inutile de marteler Firestore si l'erreur est durable
                    // (pas de réseau, règles qui refusent l'accès...).
                    delay(RETRY_DELAY_MILLIS * (attempt + 1))
                    true
                }
            }
            .catch { }
            .collect()
    }

    // Upload automatique du backlog local existant, uniquement si Firestore n'a encore aucune
    // donnée pour cet utilisateur (première connexion). Si Firestore a déjà des jeux (connexion
    // déjà faite sur un autre appareil), on ne touche pas au local : le listener démarré juste
    // après va de toute façon remplacer le contenu Room par celui de Firestore.
    private suspend fun bootstrapIfNeeded(uid: String) {
        runCatching {
            val remoteGames = firestoreDataSource.fetchGamesOnce(uid)
            if (remoteGames.isEmpty()) {
                val localGames = gameDao.observeAll().first().map { it.toDomain() }
                if (localGames.isNotEmpty()) {
                    firestoreDataSource.uploadAll(uid, localGames)
                }
            }
        }
    }

    // Miroir complet : upsert des jeux distants + suppression des lignes Room absentes du
    // snapshot distant. Acceptable en performance vu la taille d'un backlog personnel.
    private suspend fun mirrorIntoRoom(remoteGames: List<Game>) {
        val remoteIds = remoteGames.map { it.id }.toSet()
        val localIds = gameDao.getAllIds()
        localIds.filter { it !in remoteIds }.forEach { gameDao.deleteById(it) }
        remoteGames.forEach { gameDao.insert(it.toEntity()) }
    }

    // Écriture Firestore en best-effort : une erreur ici ne fait jamais échouer l'opération
    // locale — le SDK Firestore a une persistance offline native qui rejouera l'écriture toute
    // seule au retour du réseau. Pas d'écriture Firestore si personne n'est connecté (ne devrait
    // pas arriver, l'app gate tout accès aux écrans derrière la connexion, mais évite un appel
    // Firestore anonyme dans un cas limite).
    private suspend fun pushToFirestore(action: suspend (uid: String) -> Unit) {
        val uid = authRepository.currentUser.value?.uid ?: return
        runCatching { action(uid) }
    }

    private companion object {
        const val MAX_SYNC_ATTEMPTS = 5
        const val RETRY_DELAY_MILLIS = 2_000L
    }
}
