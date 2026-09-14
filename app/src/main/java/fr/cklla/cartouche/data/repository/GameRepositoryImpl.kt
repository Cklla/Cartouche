package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.local.GameDao
import fr.cklla.cartouche.data.remote.firestore.FirestoreGameDataSource
import fr.cklla.cartouche.di.ApplicationScope
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.repository.AuthRepository
import fr.cklla.cartouche.domain.repository.GameRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
                    // Vide le cache local : évite qu'un autre compte Google se connectant ensuite
                    // sur le même appareil voie le backlog du précédent.
                    gameDao.clearAll()
                } else {
                    bootstrapIfNeeded(user.uid)
                    firestoreDataSource.observeGames(user.uid).collect { games ->
                        mirrorIntoRoom(games)
                    }
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
        val gameWithId = game.copy(id = id)
        gameDao.insert(gameWithId.toEntity())
        pushToFirestore { uid -> firestoreDataSource.upsertGame(uid, gameWithId) }
        id
    }.fold(
        onSuccess = { Resource.Success(it) },
        onFailure = { Resource.Error("Impossible d'ajouter le jeu au backlog.", it) },
    )

    override suspend fun updateGame(game: Game): Resource<Unit> = runCatching {
        gameDao.update(game.toEntity())
        pushToFirestore { uid -> firestoreDataSource.upsertGame(uid, game) }
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de mettre à jour le jeu.", it) },
    )

    override suspend fun deleteGame(id: String): Resource<Unit> = runCatching {
        gameDao.deleteById(id)
        pushToFirestore { uid -> firestoreDataSource.deleteGame(uid, id) }
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de retirer le jeu du backlog.", it) },
    )

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
}
