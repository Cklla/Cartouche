package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.local.GameDao
import fr.cklla.cartouche.data.local.OutboxDao
import fr.cklla.cartouche.data.local.TransactionRunner
import fr.cklla.cartouche.data.local.entity.GameEntity
import fr.cklla.cartouche.data.remote.RemoteGameDataSource
import fr.cklla.cartouche.data.sync.GameSyncer
import fr.cklla.cartouche.data.sync.OutboxScheduler
import fr.cklla.cartouche.data.sync.enqueueDelete
import fr.cklla.cartouche.data.sync.enqueueUpsert
import fr.cklla.cartouche.di.ApplicationScope
import fr.cklla.cartouche.domain.model.AuthUser
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.repository.AuthRepository
import fr.cklla.cartouche.domain.repository.GameRepository
import fr.cklla.cartouche.domain.util.timestampForYear
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Implémentation Room + Supabase du [GameRepository].
 *
 * Room reste l'unique source lue par l'UI (`observeGames`/`observeGame`, UX offline-first, pas de
 * flicker si le serveur est indisponible). Toute écriture locale est enregistrée dans Room et dans la
 * file d'envoi (`pending_operation`) dans une même transaction, puis envoyée par [GameSyncer] dès que
 * le réseau le permet. Le serveur est la source de vérité : l'écoute temps réel sert de signal pour
 * recopier son état dans Room, sans jamais toucher à ce qui attend d'être envoyé.
 */
class GameRepositoryImpl @Inject constructor(
    private val gameDao: GameDao,
    private val outboxDao: OutboxDao,
    private val transactions: TransactionRunner,
    private val remoteDataSource: RemoteGameDataSource,
    private val syncer: GameSyncer,
    private val outboxScheduler: OutboxScheduler,
    private val authRepository: AuthRepository,
    @ApplicationScope private val repositoryScope: CoroutineScope,
) : GameRepository {

    // Sérialise les lectures-puis-écritures de la ligne d'un jeu (`addGame`, `updateGame`,
    // `setStatusYear`) : les deux dernières lisent l'état persisté avant d'écrire, et deux écritures
    // concurrentes sur le même état écraseraient l'une la date posée par l'autre.
    private val writeMutex = Mutex()

    init {
        // `collectLatest` : un changement d'utilisateur (déconnexion, ou reconnexion avec un autre
        // compte) annule proprement la synchro précédente avant d'en démarrer une nouvelle. La purge
        // passe par le même collecteur pour ne jamais tourner en même temps qu'un miroir en cours. Elle n'est déclenchée que par la déconnexion demandée : un `currentUser` à
        // `null` est aussi l'état avant que la session enregistrée soit connue, et effacerait
        // Room (donc tout le backlog local) à chaque lancement.
        repositoryScope.launch {
            merge(
                authRepository.currentUser.map { AuthEvent.UserChanged(it) },
                authRepository.signedOut.map { AuthEvent.SignedOut },
            ).collectLatest { event ->
                when (event) {
                    AuthEvent.SignedOut -> clearLocalData()
                    is AuthEvent.UserChanged -> if (event.user != null) syncWithServer()
                }
            }
        }
    }

    override fun observeGames(): Flow<List<Game>> =
        gameDao.observeAll()
            .map { entities -> entities.map { it.toDomain() } }
            // Un miroir serveur -> Room réémet souvent une liste identique (écho de l'écriture
            // locale elle-même) : éviter les recompositions inutiles.
            .distinctUntilChanged()

    override fun observeGame(id: String): Flow<Game?> =
        gameDao.observeById(id).map { it?.toDomain() }

    // Le UUID est généré ici, avant la première écriture, plutôt que délégué à Room (qui ne sait
    // pas auto-générer un id texte) : Room et le serveur doivent partager exactement le même id, et
    // c'est lui qui rend le rejeu d'un envoi idempotent.
    override suspend fun addGame(game: Game): Resource<String> = runCatching {
        val id = game.id.ifBlank { UUID.randomUUID().toString() }
        val gameWithId = game.copy(
            id = id,
            completedAt = resolveCompletedAt(previous = null, newStatus = game.status),
            abandonedAt = resolveAbandonedAt(previous = null, newStatus = game.status),
        )
        writeMutex.withLock {
            transactions.run {
                gameDao.insert(gameWithId.toEntity())
                outboxDao.enqueueUpsert(id)
            }
        }
        outboxScheduler.requestFlush()
        id
    }.fold(
        onSuccess = { Resource.Success(it) },
        onFailure = { Resource.Error("Impossible d'ajouter le jeu au backlog.", it) },
    )

    override suspend fun updateGame(game: Game): Resource<Unit> = runCatching {
        writeMutex.withLock {
            transactions.run {
                val previous = gameDao.getByIdOnce(game.id)
                gameDao.update(
                    game.copy(
                        completedAt = resolveCompletedAt(previous, game.status),
                        abandonedAt = resolveAbandonedAt(previous, game.status),
                    ).toEntity(),
                )
                outboxDao.enqueueUpsert(game.id)
            }
        }
        outboxScheduler.requestFlush()
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de mettre à jour le jeu.", it) },
    )

    // Année choisie à la main sur la fiche : écrit `completedAt` (TERMINE) ou `abandonedAt`
    // (ABANDONNE), jamais l'autre. Ne contourne pas [resolveCompletedAt] / [resolveAbandonedAt], qui
    // gardent leur rôle de dérivation aux transitions de statut et qui reprennent ensuite, tant que
    // le statut ne change pas, la valeur écrite ici.
    //
    // L'instant présent et le fuseau sont lus ici, au point d'appel, comme le fait déjà
    // [resolveCompletedAt] : le calcul lui-même est pur (voir `timestampForYear`).
    //
    // Lecture de l'état Room puis écriture sous le même verrou : sans lui, ce choix d'année et une
    // édition de note lancés coup sur coup pourraient lire le même état, et la seconde écriture
    // restaurerait l'ancienne date. Rien n'est mis en file si la valeur ne change pas.
    override suspend fun setStatusYear(gameId: String, year: Int): Resource<Unit> = runCatching {
        val changed = writeMutex.withLock {
            transactions.run {
                val entity = gameDao.getByIdOnce(gameId) ?: return@run false
                val status = runCatching { GameStatus.valueOf(entity.status) }.getOrNull()
                val currentTimestamp = when (status) {
                    GameStatus.TERMINE -> entity.completedAt
                    GameStatus.ABANDONNE -> entity.abandonedAt
                    else -> return@run false
                }
                val timestamp = timestampForYear(
                    year = year,
                    currentTimestamp = currentTimestamp,
                    releaseYear = entity.releaseYear,
                    nowMillis = System.currentTimeMillis(),
                    zone = ZoneId.systemDefault(),
                )?.takeIf { it != currentTimestamp } ?: return@run false
                val updated = if (status == GameStatus.TERMINE) {
                    entity.copy(completedAt = timestamp)
                } else {
                    entity.copy(abandonedAt = timestamp)
                }
                gameDao.update(updated)
                outboxDao.enqueueUpsert(gameId)
                true
            }
        }
        if (changed) outboxScheduler.requestFlush()
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de modifier l'année du jeu.", it) },
    )

    // Dérive automatiquement la date de complétion à chaque transition de statut, plutôt que de
    // laisser l'UI la renseigner : un seul point de vérité pour "quand un jeu est-il devenu
    // Terminé", que le changement vienne d'une sélection manuelle sur la fiche Détail ou d'ailleurs
    // à l'avenir. Seule l'année peut ensuite être ajustée, par [setStatusYear].
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

    // Même mécanique que [resolveCompletedAt], pour le statut ABANDONNE. Un jeu ne pouvant avoir
    // qu'un seul statut à la fois, passer à TERMINE efface naturellement `abandonedAt` (et
    // inversement) : chaque fonction renvoie `null` dès que `newStatus` n'est pas son statut cible.
    private fun resolveAbandonedAt(previous: GameEntity?, newStatus: GameStatus): Long? {
        val previousStatus = previous?.status?.let { runCatching { GameStatus.valueOf(it) }.getOrNull() }
        return when {
            newStatus != GameStatus.ABANDONNE -> null
            previousStatus == GameStatus.ABANDONNE -> previous?.abandonedAt
            else -> System.currentTimeMillis()
        }
    }

    override suspend fun deleteGame(id: String): Resource<Unit> = runCatching {
        writeMutex.withLock {
            transactions.run {
                gameDao.deleteById(id)
                outboxDao.enqueueDelete(id)
            }
        }
        outboxScheduler.requestFlush()
    }.fold(
        onSuccess = { Resource.Success(Unit) },
        onFailure = { Resource.Error("Impossible de retirer le jeu du backlog.", it) },
    )

    // Ne laisse rien du compte précédent sur l'appareil : ni la base Room, ni les écritures en
    // attente (elles partiraient sinon sur le compte suivant). Enveloppé dans un try parce
    // qu'une exception ici (base verrouillée) annulerait le collecteur d'événements de connexion et
    // couperait la synchro pour tout le reste de la vie du process.
    //
    // Non annulable : le passage de `currentUser` à `null` qui accompagne la déconnexion arrive en
    // général après le signal de déconnexion (il est dérivé de l'état de session, en arrière-plan),
    // et `collectLatest` annulerait alors la purge en cours, laissant le backlog du compte précédent
    // sur l'appareil. Une annulation reçue pendant la purge est relancée une fois celle-ci terminée,
    // pas avalée avec les erreurs de base.
    private suspend fun clearLocalData() {
        try {
            withContext(NonCancellable) { syncer.clearLocalData() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
        }
    }

    // Une erreur de lecture (réseau, serveur) ou de l'écoute temps réel termine le flux. Sans le
    // retry ci-dessous, la synchro s'arrêtait définitivement au premier incident, sans que rien ne le
    // signale. Le délai croît jusqu'à un plafond mais les tentatives ne s'arrêtent jamais : une app
    // lancée hors ligne doit se synchroniser dès que le réseau revient, sans relance.
    private suspend fun syncWithServer() {
        // Rejoue les écritures restées en attente d'un lancement précédent.
        outboxScheduler.requestFlush()
        flow {
            // Premier lancement du compte sur cet appareil : le backlog local que le serveur n'a pas
            // part dans la file d'envoi. Une lecture du serveur qui échoue lève ici, elle n'est jamais
            // prise pour un serveur vide.
            if (syncer.enqueueLocalOnFirstSync()) outboxScheduler.requestFlush()
            // Une première lecture sans attendre l'écoute temps réel : si celle-ci ne s'établit pas,
            // le backlog est tout de même recopié, puis relu à chaque nouvelle tentative.
            emit(Unit)
            emitAll(remoteDataSource.changes())
        }
            // Plusieurs signaux d'affilée ne donnent qu'une lecture.
            .conflate()
            .onEach { mirrorUntilApplied() }
            .retryWhen { cause, attempt ->
                SyncLog.warn("Synchro interrompue, nouvelle tentative", cause)
                delay(minOf(RETRY_DELAY_MILLIS * (attempt + 1), MAX_RETRY_DELAY_MILLIS))
                true
            }
            .collect()
    }

    // Une lecture rendue périmée par un envoi qui se terminait n'est pas appliquée : on la refait
    // peu après plutôt que d'attendre le signal suivant, qui pourrait ne jamais venir.
    private suspend fun mirrorUntilApplied() {
        while (!syncer.mirror()) delay(STALE_RETRY_MILLIS)
    }

    // Ce qui peut faire bouger la synchro : un changement d'utilisateur, ou une déconnexion demandée.
    private sealed interface AuthEvent {
        data class UserChanged(val user: AuthUser?) : AuthEvent
        data object SignedOut : AuthEvent
    }

    private companion object {
        const val RETRY_DELAY_MILLIS = 2_000L
        const val MAX_RETRY_DELAY_MILLIS = 60_000L
        const val STALE_RETRY_MILLIS = 500L
    }
}
