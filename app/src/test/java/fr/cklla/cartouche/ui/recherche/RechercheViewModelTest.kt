package fr.cklla.cartouche.ui.recherche

import fr.cklla.cartouche.data.repository.FakeGameDao
import fr.cklla.cartouche.data.repository.GameRepositoryImpl
import fr.cklla.cartouche.domain.model.GameSearchResult
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RechercheViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleResult(title: String = "Hades") =
        GameSearchResult(rawgId = 1, title = title, platform = "PC", genre = "Roguelike", year = "2020", coverUrl = null)

    @Test
    fun `changer la recherche declenche un appel apres le debounce et expose les resultats`() = runTest {
        val gameRepository = GameRepositoryImpl(FakeGameDao())
        val searchRepository = FakeGameSearchRepository().apply {
            response = Resource.Success(listOf(sampleResult()))
        }
        val viewModel = RechercheViewModel(gameRepository, searchRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("hades")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.results.size)
        assertEquals("Hades", state.results.first().title)
        assertEquals(false, state.isSearching)
        assertEquals(1, searchRepository.searchCallCount)
        collectorJob.cancel()
    }

    @Test
    fun `une recherche vide ne declenche aucun appel reseau`() = runTest {
        val gameRepository = GameRepositoryImpl(FakeGameDao())
        val searchRepository = FakeGameSearchRepository()
        val viewModel = RechercheViewModel(gameRepository, searchRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, searchRepository.searchCallCount)
        assertTrue(viewModel.uiState.value.results.isEmpty())
        collectorJob.cancel()
    }

    @Test
    fun `plusieurs frappes rapprochees ne declenchent qu'un seul appel reseau`() = runTest {
        val gameRepository = GameRepositoryImpl(FakeGameDao())
        val searchRepository = FakeGameSearchRepository().apply {
            response = Resource.Success(listOf(sampleResult()))
        }
        val viewModel = RechercheViewModel(gameRepository, searchRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("h")
        viewModel.onQueryChanged("ha")
        viewModel.onQueryChanged("had")
        viewModel.onQueryChanged("hades")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, searchRepository.searchCallCount)
        collectorJob.cancel()
    }

    @Test
    fun `une erreur reseau remonte comme message d'erreur sans resultat`() = runTest {
        val gameRepository = GameRepositoryImpl(FakeGameDao())
        val searchRepository = FakeGameSearchRepository().apply {
            response = Resource.Error("Impossible de contacter RAWG.")
        }
        val viewModel = RechercheViewModel(gameRepository, searchRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onQueryChanged("hades")
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Impossible de contacter RAWG.", state.errorMessage)
        assertTrue(state.results.isEmpty())
        collectorJob.cancel()
    }

    @Test
    fun `ajouter un resultat au backlog le fait apparaitre comme deja ajoute`() = runTest {
        val gameRepository = GameRepositoryImpl(FakeGameDao())
        val searchRepository = FakeGameSearchRepository()
        val viewModel = RechercheViewModel(gameRepository, searchRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddGame(sampleResult(title = "Hades"))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(isAlreadyAdded("hades", viewModel.uiState.value.backlogTitles))
        collectorJob.cancel()
    }
}
