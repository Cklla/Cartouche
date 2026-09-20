package fr.cklla.cartouche.ui.bibliotheque

import fr.cklla.cartouche.data.repository.FakeGameDao
import fr.cklla.cartouche.data.repository.fakeGameRepository
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.domain.model.Resource
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

@OptIn(ExperimentalCoroutinesApi::class)
class BibliothequeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectionner un filtre restreint la liste visible sans affecter les compteurs`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        repository.addGame(Game(title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.TERMINE))
        repository.addGame(Game(title = "Elden Ring", platform = "PS5", genre = "Action-RPG", status = GameStatus.A_FAIRE))

        val viewModel = BibliothequeViewModel(repository)
        // uiState est un StateFlow "WhileSubscribed" : il ne collecte le repository
        // qu'une fois observé, comme le ferait la Composable via collectAsStateWithLifecycle.
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterSelected(BacklogFilter.A_FAIRE)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.visibleGames.size)
        assertEquals("Elden Ring", state.visibleGames.first().title)
        assertEquals(2, state.filterCounts[BacklogFilter.TOUS])
        assertEquals(BacklogFilter.A_FAIRE, state.selectedFilter)

        collectorJob.cancel()
    }

    @Test
    fun `filtrer par annee de completion restreint la liste sous Termine`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val addedId = (repository.addGame(
            Game(title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.A_FAIRE),
        ) as Resource.Success).data
        repository.updateGame(
            Game(id = addedId, title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.TERMINE),
        )

        val viewModel = BibliothequeViewModel(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterSelected(BacklogFilter.TERMINE)
        dispatcher.scheduler.advanceUntilIdle()
        val availableYear = viewModel.uiState.value.availableYears.first()

        viewModel.onYearSelected(availableYear)
        dispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(1, state.visibleGames.size)
        assertEquals(availableYear, state.selectedYear)

        // Re-sélectionner la même année la désélectionne (retour à "Toutes les années").
        viewModel.onYearSelected(availableYear)
        dispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(null, state.selectedYear)

        collectorJob.cancel()
    }

    @Test
    fun `changer de filtre de statut reinitialise l'annee selectionnee`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val addedId = (repository.addGame(
            Game(title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.A_FAIRE),
        ) as Resource.Success).data
        repository.updateGame(
            Game(id = addedId, title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.TERMINE),
        )

        val viewModel = BibliothequeViewModel(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterSelected(BacklogFilter.TERMINE)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onYearSelected(viewModel.uiState.value.availableYears.first())
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onFilterSelected(BacklogFilter.TOUS)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.selectedYear)

        collectorJob.cancel()
    }
}
