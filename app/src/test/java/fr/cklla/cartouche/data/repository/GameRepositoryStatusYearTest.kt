package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.local.GameDao
import fr.cklla.cartouche.data.local.entity.GameEntity
import fr.cklla.cartouche.data.remote.firestore.FakeFirestoreGameDataSource
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.repository.GameRepository
import fr.cklla.cartouche.ui.abandonedYear
import fr.cklla.cartouche.ui.completedYear
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de `GameRepository.setStatusYear`. Les jeux sont insérés directement dans [FakeGameDao] :
 * `addGame` recalcule `completedAt` / `abandonedAt` à l'instant présent pour un jeu déjà Terminé
 * ou Abandonné, ce qui empêcherait de partir d'une date passée ou d'une date absente.
 *
 * Seules des années passées sont choisies ici (le cas « année en cours » est couvert sur la
 * fonction pure, avec un instant injecté) ; le résultat est relu via `completedYear` /
 * `abandonedYear`, les mêmes fonctions que la Bibliothèque et Stats.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameRepositoryStatusYearTest {

    private lateinit var dao: FakeGameDao
    private lateinit var firestoreDataSource: FakeFirestoreGameDataSource
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var repository: GameRepository

    private val hades = Game(
        id = "hades",
        title = "Hades",
        platform = "Switch",
        genre = "Roguelike",
        status = GameStatus.TERMINE,
        releaseYear = 2020,
    )

    private fun buildRepository(gameDao: GameDao = dao) {
        repository = GameRepositoryImpl(
            gameDao = gameDao,
            firestoreDataSource = firestoreDataSource,
            authRepository = authRepository,
            repositoryScope = CoroutineScope(UnconfinedTestDispatcher()),
        )
    }

    private suspend fun seed(game: Game) = dao.insert(game.toEntity())

    private suspend fun stored(id: String = "hades"): Game = repository.observeGame(id).first()!!

    @Before
    fun setUp() {
        dao = FakeGameDao()
        firestoreDataSource = FakeFirestoreGameDataSource()
        authRepository = FakeAuthRepository()
        buildRepository()
    }

    @Test
    fun `un jeu Termine recoit completedAt et pas abandonedAt`() = runTest {
        seed(hades.copy(completedAt = null))

        val result = repository.setStatusYear("hades", 2024)

        assertTrue(result is Resource.Success)
        assertEquals(2024, completedYear(stored()))
        assertNull(stored().abandonedAt)
    }

    @Test
    fun `un jeu Abandonne recoit abandonedAt et pas completedAt`() = runTest {
        seed(hades.copy(status = GameStatus.ABANDONNE, abandonedAt = null))

        repository.setStatusYear("hades", 2023)

        assertEquals(2023, abandonedYear(stored()))
        assertNull(stored().completedAt)
    }

    @Test
    fun `une annee deja connue est remplacee`() = runTest {
        repository.addGame(hades.copy(id = ""))
        val id = repository.observeGames().first().first().id

        repository.setStatusYear(id, 2022)

        assertEquals(2022, completedYear(stored(id)))
    }

    @Test
    fun `A faire et En cours sont un no-op`() = runTest {
        listOf(GameStatus.A_FAIRE, GameStatus.EN_COURS).forEach { status ->
            seed(hades.copy(status = status))

            val result = repository.setStatusYear("hades", 2024)

            assertTrue(result is Resource.Success)
            assertEquals(hades.copy(status = status), stored())
        }
        assertTrue(firestoreDataSource.upsertedGames.isEmpty())
    }

    @Test
    fun `un jeu inconnu ou hors backlog est un no-op`() = runTest {
        seed(hades)

        assertTrue(repository.setStatusYear("inconnu", 2024) is Resource.Success)
        assertTrue(repository.setStatusYear("", 2024) is Resource.Success)

        assertEquals(listOf("hades"), repository.observeGames().first().map { it.id })
        assertNull(stored().completedAt)
        assertTrue(firestoreDataSource.upsertedGames.isEmpty())
    }

    @Test
    fun `une annee future ou anterieure a la sortie n ecrit rien`() = runTest {
        seed(hades.copy(completedAt = null))

        repository.setStatusYear("hades", LocalDate.now().year + 1)
        repository.setStatusYear("hades", 2019)

        assertNull(stored().completedAt)
        assertTrue(firestoreDataSource.upsertedGames.isEmpty())
    }

    @Test
    fun `rechoisir la meme annee ne reecrit rien`() = runTest {
        seed(hades.copy(completedAt = null))
        repository.setStatusYear("hades", 2024)
        val firstTimestamp = stored().completedAt
        val pushesAfterFirst = firestoreDataSource.upsertedGames.size

        repository.setStatusYear("hades", 2024)

        assertEquals(firstTimestamp, stored().completedAt)
        assertEquals(pushesAfterFirst, firestoreDataSource.upsertedGames.size)
    }

    @Test
    fun `l annee choisie survit a une edition de note faite depuis une copie obsolete`() = runTest {
        seed(hades.copy(completedAt = null))
        // Copie de travail d'un écran : chargée avant le choix de l'année, donc sans date.
        val staleCopy = stored()
        repository.setStatusYear("hades", 2023)

        repository.updateGame(staleCopy.copy(rating = 5))

        assertEquals(2023, completedYear(stored()))
        assertEquals(5, stored().rating)
    }

    @Test
    fun `sortir de Termine efface l annee choisie`() = runTest {
        seed(hades.copy(completedAt = null))
        repository.setStatusYear("hades", 2023)

        repository.updateGame(stored().copy(status = GameStatus.EN_COURS))
        repository.updateGame(stored().copy(status = GameStatus.TERMINE))

        // Repasser en Terminé repart de l'instant présent : l'année choisie est perdue.
        assertEquals(LocalDate.now().year, completedYear(stored()))
    }

    @Test
    fun `sortir d Abandonne efface l annee choisie`() = runTest {
        seed(hades.copy(status = GameStatus.ABANDONNE, abandonedAt = null))
        repository.setStatusYear("hades", 2023)

        repository.updateGame(stored().copy(status = GameStatus.A_FAIRE))

        assertNull(stored().abandonedAt)
    }

    @Test
    fun `passer de Termine a Abandonne vide un champ et pose l instant present dans l autre`() = runTest {
        seed(hades.copy(completedAt = null))
        repository.setStatusYear("hades", 2023)

        repository.updateGame(stored().copy(status = GameStatus.ABANDONNE))

        assertNull(stored().completedAt)
        assertEquals(LocalDate.now().year, abandonedYear(stored()))
    }

    @Test
    fun `passer d Abandonne a Termine vide un champ et pose l instant present dans l autre`() = runTest {
        seed(hades.copy(status = GameStatus.ABANDONNE, abandonedAt = null))
        repository.setStatusYear("hades", 2023)

        repository.updateGame(stored().copy(status = GameStatus.TERMINE))

        assertNull(stored().abandonedAt)
        assertEquals(LocalDate.now().year, completedYear(stored()))
    }

    @Test
    fun `l annee choisie est poussee vers Firestore`() = runTest {
        seed(hades.copy(completedAt = null))

        repository.setStatusYear("hades", 2024)

        val pushed = firestoreDataSource.upsertedGames.last()
        assertEquals("hades", pushed.id)
        assertEquals(2024, completedYear(pushed))
    }

    @Test
    fun `un echec Firestore n annule pas l ecriture locale`() = runTest {
        seed(hades.copy(completedAt = null))
        firestoreDataSource.shouldThrowOnWrite = true

        val result = repository.setStatusYear("hades", 2024)

        assertTrue(result is Resource.Success)
        assertEquals(2024, completedYear(stored()))
    }

    @Test
    fun `un echec Room remonte une erreur structuree`() = runTest {
        buildRepository(
            object : GameDao by dao {
                override suspend fun update(game: GameEntity) = error("Échec Room simulé")
            },
        )
        seed(hades.copy(completedAt = null))

        val result = repository.setStatusYear("hades", 2024)

        assertTrue(result is Resource.Error)
    }

    @Test
    fun `une edition et un choix d annee concurrents ne s ecrasent pas`() = runTest {
        // Chaque lecture cède la main : sans verrou, les deux écritures partiraient du même état
        // Room et la seconde effacerait ce que la première vient d'écrire.
        buildRepository(
            object : GameDao by dao {
                override suspend fun getByIdOnce(id: String): GameEntity? = dao.getByIdOnce(id).also { yield() }
            },
        )
        seed(hades.copy(completedAt = null))
        val staleCopy = stored()

        listOf(
            async { repository.updateGame(staleCopy.copy(notes = "à rejouer")) },
            async { repository.setStatusYear("hades", 2023) },
        ).awaitAll()

        assertEquals("à rejouer", stored().notes)
        assertEquals(2023, completedYear(stored()))
    }
}
