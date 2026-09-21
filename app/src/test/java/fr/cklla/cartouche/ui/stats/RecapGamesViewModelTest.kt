package fr.cklla.cartouche.ui.stats

import androidx.lifecycle.SavedStateHandle
import fr.cklla.cartouche.data.repository.FakeGameDao
import fr.cklla.cartouche.data.repository.fakeGameRepository
import fr.cklla.cartouche.data.repository.toEntity
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.domain.repository.GameRepository
import fr.cklla.cartouche.ui.bibliotheque.BacklogFilter
import fr.cklla.cartouche.ui.navigation.CartoucheDestinations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * `dao.insert` (directement, plutôt que `repository.addGame`) pour poser des `completedAt`/
 * `abandonedAt` figés : `GameRepositoryImpl.addGame` recalcule toujours ces champs à "maintenant"
 * pour un jeu Terminé/Abandonné dès l'ajout (voir `resolveCompletedAt`/`resolveAbandonedAt`), donc
 * une valeur passée à `Game(...)` via `addGame` ne survivrait pas — même contrainte que
 * `StatsViewModelTest` contourne en repassant par `updateGame`, ici plus simple d'insérer
 * directement dans le DAO fake puisqu'on veut deux années différentes dans le même test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecapGamesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    // Milieu d'année en UTC, comme dans StatsCalculationsTest.
    private val completedIn2024 = 1_719_792_000_000L // 2024-07-01T00:00:00Z
    private val completedIn2023 = 1_688_169_600_000L // 2023-07-01T00:00:00Z

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: GameRepository, year: Int, filter: BacklogFilter) = RecapGamesViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                CartoucheDestinations.RECAP_ARG_YEAR to year,
                CartoucheDestinations.RECAP_GAMES_ARG_FILTER to filter.name,
            ),
        ),
        gameRepository = repository,
    )

    @Test
    fun `games ne liste que les jeux termines de l'annee ciblee`() = runTest {
        val dao = FakeGameDao()
        dao.insert(Game(id = "1", title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.TERMINE, completedAt = completedIn2024).toEntity())
        dao.insert(Game(id = "2", title = "Celeste", platform = "PC", genre = "Plateforme", status = GameStatus.TERMINE, completedAt = completedIn2023).toEntity())
        dao.insert(Game(id = "3", title = "Elden Ring", platform = "PS5", genre = "Action-RPG", status = GameStatus.A_FAIRE).toEntity())
        val repository = fakeGameRepository(dao)

        val viewModel = viewModel(repository, year = 2024, filter = BacklogFilter.TERMINE)
        val collectorJob = launch { viewModel.games.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Hades"), viewModel.games.value.map { it.title })

        collectorJob.cancel()
    }

    @Test
    fun `games liste les jeux abandonnes quand le filtre est ABANDONNE`() = runTest {
        val dao = FakeGameDao()
        dao.insert(Game(id = "1", title = "Cyberpunk 2077", platform = "PC", genre = "Action-RPG", status = GameStatus.ABANDONNE, abandonedAt = completedIn2024).toEntity())
        dao.insert(Game(id = "2", title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.TERMINE, completedAt = completedIn2024).toEntity())
        val repository = fakeGameRepository(dao)

        val viewModel = viewModel(repository, year = 2024, filter = BacklogFilter.ABANDONNE)
        val collectorJob = launch { viewModel.games.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Cyberpunk 2077"), viewModel.games.value.map { it.title })

        collectorJob.cancel()
    }
}
