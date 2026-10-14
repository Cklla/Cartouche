package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.local.TransactionRunner
import fr.cklla.cartouche.data.remote.FakeRemoteGameDataSource
import fr.cklla.cartouche.data.sync.GameSyncer
import fr.cklla.cartouche.data.sync.PendingOperationKind
import fr.cklla.cartouche.domain.model.AuthUser
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.repository.GameRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `UnconfinedTestDispatcher` pour le scope interne du repository : les coroutines qu'il lance
 * (écoute de `currentUser`, miroir serveur -> Room) s'exécutent alors de façon synchrone et
 * déterministe dès qu'un `MutableStateFlow` fake change de valeur, sans avoir besoin d'avancer un
 * temps virtuel séparé — le test reste single-thread de bout en bout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameRepositoryImplTest {

    private lateinit var dao: FakeGameDao
    private lateinit var outboxDao: FakeOutboxDao
    private lateinit var remote: FakeRemoteGameDataSource
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var syncer: GameSyncer
    private lateinit var scheduler: TestOutboxScheduler
    private lateinit var repository: GameRepository

    private val hades = Game(
        title = "Hades",
        platform = "Switch",
        genre = "Roguelike",
        status = GameStatus.A_FAIRE,
    )

    private fun buildRepository(
        scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
        syncer: GameSyncer = fakeSyncer(remote, outboxDao, dao, authRepository),
    ) {
        this.syncer = syncer
        scheduler = TestOutboxScheduler({ syncer.flush() })
        repository = GameRepositoryImpl(
            gameDao = dao,
            outboxDao = outboxDao,
            transactions = ImmediateTransactionRunner,
            remoteDataSource = remote,
            syncer = syncer,
            outboxScheduler = scheduler,
            authRepository = authRepository,
            repositoryScope = scope,
        )
    }

    @Before
    fun setUp() {
        dao = FakeGameDao()
        outboxDao = FakeOutboxDao()
        remote = FakeRemoteGameDataSource()
        authRepository = FakeAuthRepository()
        buildRepository()
    }

    @Test
    fun `addGame rend le jeu visible via observeGames`() = runTest {
        val result = repository.addGame(hades)

        assertTrue(result is Resource.Success)
        val games = repository.observeGames().first()
        assertEquals(1, games.size)
        assertEquals("Hades", games.first().title)
    }

    @Test
    fun `updateGame modifie le statut du jeu observe`() = runTest {
        val addedId = (repository.addGame(hades) as Resource.Success).data
        val enCours = hades.copy(id = addedId, status = GameStatus.EN_COURS)

        repository.updateGame(enCours)

        val games = repository.observeGames().first()
        assertEquals(GameStatus.EN_COURS, games.first().status)
    }

    @Test
    fun `deleteGame retire le jeu du backlog`() = runTest {
        val addedId = (repository.addGame(hades) as Resource.Success).data

        repository.deleteGame(addedId)

        assertTrue(repository.observeGames().first().isEmpty())
    }

    @Test
    fun `addGame renvoie une erreur structuree si le dao echoue`() = runTest {
        dao.shouldThrowOnInsert = true

        val result = repository.addGame(hades)

        assertTrue(result is Resource.Error)
        assertTrue(outboxDao.pending.isEmpty())
    }

    @Test
    fun `addGame repercute le jeu vers le serveur`() = runTest {
        val addedId = (repository.addGame(hades) as Resource.Success).data

        assertEquals(listOf(addedId), remote.upsertedGames.map { it.id })
    }

    @Test
    fun `un serveur injoignable n'empeche pas l'ecriture locale`() = runTest {
        remote.offline = true

        val result = repository.addGame(hades)

        assertTrue(result is Resource.Success)
        assertEquals(1, repository.observeGames().first().size)
        assertEquals(1, outboxDao.pending.size)
    }

    // --- déconnexion ---

    @Test
    fun `deconnexion vide le backlog local`() = runTest {
        repository.addGame(hades)
        assertEquals(1, repository.observeGames().first().size)

        authRepository.signOut()

        assertTrue(repository.observeGames().first().isEmpty())
    }

    @Test
    fun `deconnexion vide aussi la file d'envoi`() = runTest {
        remote.offline = true
        repository.addGame(hades)
        assertEquals(1, outboxDao.pending.size)

        authRepository.signOut()

        assertTrue(outboxDao.pending.isEmpty())
    }

    @Test
    fun `un lancement sans session ne vide pas le backlog local`() = runTest {
        // Au lancement, `currentUser` vaut `null` le temps que la session enregistrée soit connue :
        // ce n'est pas une déconnexion et rien ne doit être effacé.
        authRepository = FakeAuthRepository(user = null)
        dao.insert(hades.copy(id = "local-1").toEntity())

        buildRepository()

        assertEquals(1, repository.observeGames().first().size)
    }

    @Test
    fun `une session absente sans deconnexion ne vide rien`() = runTest {
        remote.offline = true
        repository.addGame(hades)

        authRepository.dropSessionWithoutSignOut()

        assertEquals(1, repository.observeGames().first().size)
        assertEquals(1, outboxDao.pending.size)
    }

    @Test
    fun `la purge n'est pas annulee par la session qui passe a null apres le signal de deconnexion`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var holdNextTransaction = false
        val gatedTransactions = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T {
                if (holdNextTransaction) {
                    holdNextTransaction = false
                    gate.await()
                }
                return block()
            }
        }
        // Compte neuf : le repository de `setUp`, abonné à l'ancien, purgerait les mêmes DAO de son côté.
        authRepository = FakeAuthRepository()
        buildRepository(syncer = fakeSyncer(remote, outboxDao, dao, authRepository, transactions = gatedTransactions))
        remote.offline = true
        repository.addGame(hades)
        holdNextTransaction = true

        // Ordre de production : le signal part avant que `currentUser` ne repasse à `null`, ce qui
        // fait annuler par `collectLatest` la purge qui vient de démarrer (suspendue sur `gate`).
        authRepository.emitSignedOutBeforeSessionUpdate()
        authRepository.dropSessionWithoutSignOut()
        gate.complete(Unit)

        assertTrue(repository.observeGames().first().isEmpty())
        assertTrue(outboxDao.pending.isEmpty())
    }

    @Test
    fun `une purge en echec n'arrete pas la collecte`() = runTest {
        var failNextTransaction = false
        val flakyTransactions = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T {
                if (failNextTransaction) {
                    failNextTransaction = false
                    error("Base verrouillée (simulé)")
                }
                return block()
            }
        }
        authRepository = FakeAuthRepository(user = null)
        buildRepository(syncer = fakeSyncer(remote, outboxDao, dao, authRepository, transactions = flakyTransactions))
        authRepository.signInAs(AuthUser(uid = "u1", displayName = "Joueur"))

        // La purge de la déconnexion échoue : l'exception est avalée, la collecte continue.
        failNextTransaction = true
        authRepository.signOut()
        remote.games.value = listOf(hades.copy(id = "distant-1"))
        authRepository.signInAs(AuthUser(uid = "u2", displayName = "Joueur"))

        // Sans la garde, l'exception aurait tué le collecteur et la synchro du compte suivant
        // ne serait jamais repartie.
        assertEquals(listOf("distant-1"), repository.observeGames().first().map { it.id })
    }

    // --- synchro ---

    @Test
    fun `la synchro reprend apres une erreur d'ecoute`() = runTest {
        // Même horloge virtuelle que le test, sinon les délais entre deux tentatives
        // n'avanceraient jamais.
        authRepository = FakeAuthRepository(user = null)
        remote = FakeRemoteGameDataSource().apply { failedChangesAttempts = 2 }
        buildRepository(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        remote.games.value = listOf(hades.copy(id = "distant-1"))

        authRepository.signInAs(AuthUser(uid = "user", displayName = "Joueur"))
        advanceUntilIdle()

        // Sans retry, les deux premières erreurs auraient coupé la synchro définitivement et le
        // jeu distant ne serait jamais arrivé jusqu'à Room.
        assertEquals(3, remote.changesCallCount)
        assertEquals(listOf("distant-1"), repository.observeGames().first().map { it.id })
    }

    @Test
    fun `le backlog distant est recopie meme si l'ecoute temps reel ne s'etablit jamais`() = runTest {
        authRepository = FakeAuthRepository(user = null)
        remote = FakeRemoteGameDataSource().apply {
            changesNeverSubscribe = true
            games.value = listOf(hades.copy(id = "distant-1"))
        }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        buildRepository(scope)

        authRepository.signInAs(AuthUser(uid = "user", displayName = "Joueur"))
        runCurrent()

        // Réinstallation avec Realtime en panne : sans lecture initiale, rien ne revenait du serveur.
        assertEquals(listOf("distant-1"), repository.observeGames().first().map { it.id })
        scope.cancel()
    }

    @Test
    fun `lance hors ligne, la synchro demarre quand le reseau revient sans relancer l'app`() = runTest {
        authRepository = FakeAuthRepository(user = null)
        remote = FakeRemoteGameDataSource().apply {
            offline = true
            games.value = listOf(hades.copy(id = "distant-1"))
        }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        buildRepository(scope)
        authRepository.signInAs(AuthUser(uid = "user", displayName = "Joueur"))
        // Bien plus d'échecs que l'ancienne limite de 5 tentatives.
        testScheduler.advanceTimeBy(10 * 60_000L)
        assertTrue(repository.observeGames().first().isEmpty())

        remote.offline = false
        advanceUntilIdle()

        assertEquals(listOf("distant-1"), repository.observeGames().first().map { it.id })
        scope.cancel()
    }

    @Test
    fun `mirrorIntoRoom reflete un ajout distant`() = runTest {
        remote.games.value = listOf(hades.copy(id = "distant-1"))

        assertEquals(listOf("distant-1"), repository.observeGames().first().map { it.id })
    }

    @Test
    fun `mirrorIntoRoom supprime localement un jeu retire du serveur`() = runTest {
        remote.games.value = listOf(hades.copy(id = "distant-1"))
        assertEquals(1, repository.observeGames().first().size)

        remote.games.value = emptyList()

        assertTrue(repository.observeGames().first().isEmpty())
    }

    // --- premier lancement ---

    @Test
    fun `un serveur vide au premier lancement recoit le backlog local`() = runTest {
        // Backlog local présent avant toute connexion (déconnecté au départ).
        authRepository = FakeAuthRepository(user = null)
        buildRepository()
        dao.insert(hades.copy(id = "local-1").toEntity())

        authRepository.signInAs(AuthUser(uid = "new-user", displayName = "Joueur"))

        assertEquals(listOf("local-1"), remote.games.value.map { it.id })
        // Le backlog local n'a jamais été vidé en route.
        assertEquals(listOf("local-1"), dao.getAllIds())
        assertTrue(outboxDao.pending.isEmpty())
    }

    @Test
    fun `le backlog local est protege du miroir tant que le serveur vide n'a rien recu`() = runTest {
        authRepository = FakeAuthRepository(user = null)
        buildRepository()
        dao.insert(hades.copy(id = "local-1").toEntity())
        scheduler.autoFlush = false

        authRepository.signInAs(AuthUser(uid = "new-user", displayName = "Joueur"))
        // Un état serveur vide (rien n'est encore parti) ne doit rien supprimer.
        assertTrue(syncer.mirror())

        assertEquals(listOf("local-1"), dao.getAllIds())
        assertEquals(1, outboxDao.pending.size)
    }

    @Test
    fun `une lecture du serveur qui echoue au premier lancement n'est pas prise pour un serveur vide`() = runTest {
        authRepository = FakeAuthRepository(user = null)
        remote = FakeRemoteGameDataSource().apply { offline = true }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        buildRepository(scope)
        dao.insert(hades.copy(id = "local-1").toEntity())

        authRepository.signInAs(AuthUser(uid = "new-user", displayName = "Joueur"))
        testScheduler.advanceTimeBy(10_000L)

        // Aucune mise en file sur une lecture ratée, et rien d'effacé localement.
        assertTrue(outboxDao.pending.isEmpty())
        assertEquals(listOf("local-1"), dao.getAllIds())
        // La synchro réessaie sans fin tant que le réseau manque : on l'arrête pour finir le test.
        scope.cancel()
    }

    @Test
    fun `un serveur deja peuple est complete par le backlog local au premier lancement sans rien perdre`() = runTest {
        authRepository = FakeAuthRepository(user = null)
        remote = FakeRemoteGameDataSource().apply { games.value = listOf(hades.copy(id = "distant-1")) }
        buildRepository()
        dao.insert(hades.copy(id = "local-1").toEntity())

        authRepository.signInAs(AuthUser(uid = "existing-user", displayName = "Joueur"))

        assertEquals(listOf("local-1"), remote.upsertedGames.map { it.id })
        assertEquals(setOf("distant-1", "local-1"), repository.observeGames().first().mapTo(mutableSetOf()) { it.id })
        assertEquals(setOf("distant-1", "local-1"), remote.games.value.mapTo(mutableSetOf()) { it.id })
    }

    // --- file d'envoi : écriture hors ligne puis miroir ---

    @Test
    fun `un jeu ajoute hors ligne reste en local apres un miroir du serveur vide`() = runTest {
        remote.offline = true
        val id = (repository.addGame(hades) as Resource.Success).data
        remote.offline = false

        assertTrue(syncer.mirror())

        assertEquals(listOf(id), dao.getAllIds())
        assertEquals(1, outboxDao.pending.size)
    }

    @Test
    fun `un jeu ajoute hors ligne part vers le serveur au retour du reseau`() = runTest {
        remote.offline = true
        val id = (repository.addGame(hades) as Resource.Success).data
        assertTrue(remote.games.value.isEmpty())

        remote.offline = false
        syncer.flush()

        assertEquals(listOf(id), remote.games.value.map { it.id })
        assertTrue(outboxDao.pending.isEmpty())
    }

    @Test
    fun `une modification hors ligne n'est pas ecrasee par le miroir`() = runTest {
        val id = (repository.addGame(hades) as Resource.Success).data
        remote.offline = true
        repository.updateGame(hades.copy(id = id, status = GameStatus.EN_COURS))
        remote.offline = false

        assertTrue(syncer.mirror())

        assertEquals(GameStatus.EN_COURS, repository.observeGame(id).first()?.status)
        syncer.flush()
        assertEquals(GameStatus.EN_COURS, remote.games.value.single().status)
    }

    @Test
    fun `une suppression en attente n'est pas recreee par le miroir`() = runTest {
        val id = (repository.addGame(hades) as Resource.Success).data
        remote.offline = true
        repository.deleteGame(id)
        remote.offline = false

        assertTrue(syncer.mirror())

        assertTrue(dao.getAllIds().isEmpty())
        syncer.flush()
        assertTrue(remote.games.value.isEmpty())
        assertEquals(listOf(id), remote.deletedGameIds)
    }

    @Test
    fun `supprimer un jeu retire ses operations en attente et n'enfile que la suppression`() = runTest {
        remote.offline = true
        val id = (repository.addGame(hades) as Resource.Success).data
        repository.deleteGame(id)

        assertEquals(listOf(PendingOperationKind.DELETE_GAME.name), outboxDao.pending.map { it.kind })
    }

    @Test
    fun `chaque ecriture locale enregistre Room et la file ensemble`() = runTest {
        remote.offline = true
        val id = (repository.addGame(hades) as Resource.Success).data
        repository.updateGame(hades.copy(id = id, status = GameStatus.EN_COURS))
        repository.deleteGame(id)

        // Rien n'est envoyé directement : tout est passé par la file.
        assertTrue(remote.upsertedGames.isEmpty())
        assertTrue(remote.deletedGameIds.isEmpty())
        assertTrue(scheduler.requestCount >= 3)
    }

    @Test
    fun `un etat serveur lu avant un envoi reussi est ignore`() = runTest {
        val id = (repository.addGame(hades) as Resource.Success).data
        // Le jeu est en file hors ligne ; pendant la lecture (serveur vide), l'envoi réussit et sort de
        // la file. Appliquer l'état lu avant l'envoi supprimerait le jeu qui vient d'arriver.
        remote.offline = true
        repository.updateGame(hades.copy(id = id, status = GameStatus.EN_COURS))
        remote.offline = false
        var firstFetch = true
        remote.onFetch = {
            if (firstFetch) {
                firstFetch = false
                syncer.flush()
            }
        }

        assertTrue(syncer.mirror())

        assertEquals(listOf(id), dao.getAllIds())
        assertEquals(1, remote.games.value.size)
        assertTrue(remote.fetchCount >= 2)
    }

    // --- dates de fin et d'abandon ---

    @Test
    fun `passer un jeu a Termine renseigne completedAt`() = runTest {
        val addedId = (repository.addGame(hades) as Resource.Success).data

        repository.updateGame(hades.copy(id = addedId, status = GameStatus.TERMINE))

        val game = repository.observeGames().first().first()
        assertTrue(game.completedAt != null)
    }

    @Test
    fun `rester Termine ne deplace pas completedAt`() = runTest {
        val addedId = (repository.addGame(hades) as Resource.Success).data
        repository.updateGame(hades.copy(id = addedId, status = GameStatus.TERMINE))
        val firstCompletedAt = repository.observeGames().first().first().completedAt

        // Une simple édition (note perso) sans changement de statut ne doit pas re-timestamper.
        repository.updateGame(hades.copy(id = addedId, status = GameStatus.TERMINE, rating = 5))

        val secondCompletedAt = repository.observeGames().first().first().completedAt
        assertEquals(firstCompletedAt, secondCompletedAt)
    }

    @Test
    fun `sortir de Termine efface completedAt`() = runTest {
        val addedId = (repository.addGame(hades) as Resource.Success).data
        repository.updateGame(hades.copy(id = addedId, status = GameStatus.TERMINE))

        repository.updateGame(hades.copy(id = addedId, status = GameStatus.EN_COURS))

        val game = repository.observeGames().first().first()
        assertEquals(null, game.completedAt)
    }

    @Test
    fun `passer un jeu a Abandonne renseigne abandonedAt`() = runTest {
        val addedId = (repository.addGame(hades) as Resource.Success).data

        repository.updateGame(hades.copy(id = addedId, status = GameStatus.ABANDONNE))

        val game = repository.observeGames().first().first()
        assertTrue(game.abandonedAt != null)
    }

    @Test
    fun `rester Abandonne ne deplace pas abandonedAt`() = runTest {
        val addedId = (repository.addGame(hades) as Resource.Success).data
        repository.updateGame(hades.copy(id = addedId, status = GameStatus.ABANDONNE))
        val firstAbandonedAt = repository.observeGames().first().first().abandonedAt

        repository.updateGame(hades.copy(id = addedId, status = GameStatus.ABANDONNE, rating = 2))

        val secondAbandonedAt = repository.observeGames().first().first().abandonedAt
        assertEquals(firstAbandonedAt, secondAbandonedAt)
    }

    @Test
    fun `sortir d'Abandonne efface abandonedAt`() = runTest {
        val addedId = (repository.addGame(hades) as Resource.Success).data
        repository.updateGame(hades.copy(id = addedId, status = GameStatus.ABANDONNE))

        repository.updateGame(hades.copy(id = addedId, status = GameStatus.EN_COURS))

        val game = repository.observeGames().first().first()
        assertEquals(null, game.abandonedAt)
    }

    @Test
    fun `passer d'Abandonne a Termine bascule completedAt et efface abandonedAt`() = runTest {
        val addedId = (repository.addGame(hades) as Resource.Success).data
        repository.updateGame(hades.copy(id = addedId, status = GameStatus.ABANDONNE))

        repository.updateGame(hades.copy(id = addedId, status = GameStatus.TERMINE))

        val game = repository.observeGames().first().first()
        assertTrue(game.completedAt != null)
        assertEquals(null, game.abandonedAt)
    }
}
