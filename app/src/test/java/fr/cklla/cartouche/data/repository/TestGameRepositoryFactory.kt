package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.local.GameDao
import fr.cklla.cartouche.data.local.OutboxDao
import fr.cklla.cartouche.data.local.TransactionRunner
import fr.cklla.cartouche.data.remote.FakeRemoteGameDataSource
import fr.cklla.cartouche.data.sync.GameSyncer
import fr.cklla.cartouche.data.sync.InitialUploadStore
import fr.cklla.cartouche.data.sync.OutboxScheduler
import fr.cklla.cartouche.domain.repository.AuthRepository
import fr.cklla.cartouche.domain.repository.GameRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** Transaction sans effet : les DAO en mémoire n'ont ni transaction ni retour arrière. */
object ImmediateTransactionRunner : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

/**
 * Remplace WorkManager : exécute l'envoi tout de suite (la portée non confinée le déroule en ligne,
 * le faux serveur ne suspendant jamais), ou le laisse en attente quand [autoFlush] est à `false` pour
 * tester ce qui se passe tant que la file n'est pas partie.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestOutboxScheduler(
    private val flush: suspend () -> Unit,
    private val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
) : OutboxScheduler {

    var autoFlush = true
    var requestCount = 0
        private set

    override fun requestFlush() {
        requestCount++
        if (autoFlush) scope.launch { flush() }
    }
}

/** Mémoire de l'envoi initial en mémoire : le test peut la préremplir ou la relire. */
class FakeInitialUploadStore(var doneFor: String? = null) : InitialUploadStore {
    override fun markedFor() = doneFor
    override fun markDone(userId: String) {
        doneFor = userId
    }

    override fun clear() {
        doneFor = null
    }
}

fun fakeSyncer(
    remote: FakeRemoteGameDataSource = FakeRemoteGameDataSource(),
    outboxDao: OutboxDao = FakeOutboxDao(),
    gameDao: GameDao = FakeGameDao(),
    authRepository: AuthRepository = FakeAuthRepository(),
    transactions: TransactionRunner = ImmediateTransactionRunner,
    initialUpload: InitialUploadStore = FakeInitialUploadStore(),
) = GameSyncer(remote, outboxDao, gameDao, transactions, authRepository, initialUpload)

/**
 * Construit un [GameRepositoryImpl] de test, avec des fakes par défaut pour le serveur/l'auth : la
 * plupart des tests de ViewModel n'ont besoin que de Room (via [dao]) et n'observent pas la synchro
 * elle-même (couverte par `GameRepositoryImplTest` et `GameSyncerTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun fakeGameRepository(
    dao: GameDao,
    remote: FakeRemoteGameDataSource = FakeRemoteGameDataSource(),
    authRepository: FakeAuthRepository = FakeAuthRepository(),
    outboxDao: OutboxDao = FakeOutboxDao(),
    syncer: GameSyncer = fakeSyncer(remote, outboxDao, dao, authRepository),
    scheduler: OutboxScheduler = TestOutboxScheduler({ syncer.flush() }),
): GameRepository = GameRepositoryImpl(
    gameDao = dao,
    outboxDao = outboxDao,
    transactions = ImmediateTransactionRunner,
    remoteDataSource = remote,
    syncer = syncer,
    outboxScheduler = scheduler,
    authRepository = authRepository,
    repositoryScope = CoroutineScope(UnconfinedTestDispatcher()),
)
