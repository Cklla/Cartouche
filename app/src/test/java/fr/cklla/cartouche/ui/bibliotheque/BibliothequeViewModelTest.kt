package fr.cklla.cartouche.ui.bibliotheque

import fr.cklla.cartouche.data.repository.FakeGameDao
import fr.cklla.cartouche.data.repository.fakeGameRepository
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
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
}
