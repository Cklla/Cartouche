package fr.cklla.cartouche.ui.detail

import androidx.lifecycle.SavedStateHandle
import fr.cklla.cartouche.data.repository.FakeGameDao
import fr.cklla.cartouche.data.repository.GameRepositoryImpl
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.domain.model.Resource
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun setUpGame(repository: GameRepositoryImpl): Long {
        val result = repository.addGame(
            Game(title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.A_FAIRE),
        )
        return (result as Resource.Success).data
    }

    private fun viewModel(repository: GameRepositoryImpl, gameId: Long) = DetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(CartoucheDestinations.DETAIL_ARG_GAME_ID to gameId)),
        gameRepository = repository,
    )

    @Test
    fun `changer le statut met a jour le jeu observe`() = runTest {
        val dao = FakeGameDao()
        val repository = GameRepositoryImpl(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusSelected(GameStatus.TERMINE)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(GameStatus.TERMINE, viewModel.uiState.value.game?.status)
        collectorJob.cancel()
    }

    @Test
    fun `le temps de jeu ne descend jamais sous zero`() = runTest {
        val dao = FakeGameDao()
        val repository = GameRepositoryImpl(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onHoursDecrement()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.game?.userPlaytimeHours)
        collectorJob.cancel()
    }

    @Test
    fun `incrementer puis decrementer le temps de jeu revient a la valeur initiale`() = runTest {
        val dao = FakeGameDao()
        val repository = GameRepositoryImpl(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onHoursIncrement()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onHoursIncrement()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onHoursDecrement()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.game?.userPlaytimeHours)
        collectorJob.cancel()
    }

    @Test
    fun `noter et modifier les notes libres met a jour le jeu`() = runTest {
        val dao = FakeGameDao()
        val repository = GameRepositoryImpl(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onRatingSelected(4)
        viewModel.onNotesChanged("Super jeu")
        dispatcher.scheduler.advanceUntilIdle()

        val game = viewModel.uiState.value.game
        assertEquals(4, game?.rating)
        assertEquals("Super jeu", game?.notes)
        collectorJob.cancel()
    }

    @Test
    fun `retirer le jeu du backlog fait disparaitre le jeu observe`() = runTest {
        val dao = FakeGameDao()
        val repository = GameRepositoryImpl(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onRemoveGame()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.game)
        assertEquals(false, viewModel.uiState.value.isLoading)
        collectorJob.cancel()
    }
}
