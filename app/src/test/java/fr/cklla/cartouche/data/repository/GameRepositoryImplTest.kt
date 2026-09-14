package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.remote.firestore.FakeFirestoreGameDataSource
import fr.cklla.cartouche.domain.model.AuthUser
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.repository.GameRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `UnconfinedTestDispatcher` pour le scope interne du repository : les coroutines qu'il lance
 * (écoute de `currentUser`, mirroring Firestore -> Room) s'exécutent alors de façon synchrone et
 * déterministe dès qu'un `MutableStateFlow` fake change de valeur, sans avoir besoin d'avancer un
 * temps virtuel séparé — le test reste single-thread de bout en bout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameRepositoryImplTest {

    private lateinit var dao: FakeGameDao
    private lateinit var firestoreDataSource: FakeFirestoreGameDataSource
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var repository: GameRepository

    private val hades = Game(
        title = "Hades",
        platform = "Switch",
        genre = "Roguelike",
        status = GameStatus.A_FAIRE,
    )

    private fun buildRepository() {
        repository = GameRepositoryImpl(
            gameDao = dao,
            firestoreDataSource = firestoreDataSource,
            authRepository = authRepository,
            repositoryScope = CoroutineScope(UnconfinedTestDispatcher()),
        )
    }

    @Before
    fun setUp() {
        dao = FakeGameDao()
        firestoreDataSource = FakeFirestoreGameDataSource()
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
    }

    @Test
    fun `addGame repercute le jeu vers Firestore`() = runTest {
        val addedId = (repository.addGame(hades) as Resource.Success).data

        assertEquals(listOf(addedId), firestoreDataSource.upsertedGames.map { it.id })
    }

    @Test
    fun `un echec Firestore n'empeche pas l'ecriture locale`() = runTest {
        firestoreDataSource.shouldThrowOnWrite = true

        val result = repository.addGame(hades)

        assertTrue(result is Resource.Success)
        assertEquals(1, repository.observeGames().first().size)
    }

    @Test
    fun `deconnexion vide le backlog local`() = runTest {
        repository.addGame(hades)
        assertEquals(1, repository.observeGames().first().size)

        authRepository.signOut()

        assertTrue(repository.observeGames().first().isEmpty())
    }

    @Test
    fun `mirrorIntoRoom reflete un ajout distant`() = runTest {
        val distant = hades.copy(id = "distant-1")

        firestoreDataSource.remoteGames.value = listOf(distant)

        val games = repository.observeGames().first()
        assertEquals(listOf("distant-1"), games.map { it.id })
    }

    @Test
    fun `mirrorIntoRoom supprime localement un jeu retire de Firestore`() = runTest {
        val distant = hades.copy(id = "distant-1")
        firestoreDataSource.remoteGames.value = listOf(distant)
        assertEquals(1, repository.observeGames().first().size)

        firestoreDataSource.remoteGames.value = emptyList()

        assertTrue(repository.observeGames().first().isEmpty())
    }

    @Test
    fun `bootstrap uploade le backlog local si Firestore est vide a la connexion`() = runTest {
        // Backlog local présent avant toute connexion (déconnecté au départ).
        authRepository = FakeAuthRepository(user = null)
        firestoreDataSource = FakeFirestoreGameDataSource()
        buildRepository()
        dao.insert(hades.copy(id = "local-1").toEntity())

        authRepository.signInAs(AuthUser(uid = "new-user", displayName = "Joueur"))

        assertEquals(1, firestoreDataSource.uploadAllCallCount)
        assertEquals(listOf("local-1"), firestoreDataSource.remoteGames.value.map { it.id })
    }

    @Test
    fun `bootstrap n'ecrase pas Firestore si des jeux y sont deja presents`() = runTest {
        val distant = hades.copy(id = "distant-1")
        authRepository = FakeAuthRepository(user = null)
        firestoreDataSource = FakeFirestoreGameDataSource().apply { remoteGames.value = listOf(distant) }
        buildRepository()
        dao.insert(hades.copy(id = "local-1").toEntity())

        authRepository.signInAs(AuthUser(uid = "existing-user", displayName = "Joueur"))

        assertEquals(0, firestoreDataSource.uploadAllCallCount)
        // Le miroir Firestore -> Room fait ensuite autorité : le jeu distant remplace le local.
        val games = repository.observeGames().first()
        assertEquals(listOf("distant-1"), games.map { it.id })
    }
}
