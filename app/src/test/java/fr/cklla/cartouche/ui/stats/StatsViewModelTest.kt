package fr.cklla.cartouche.ui.stats

import fr.cklla.cartouche.data.repository.FakeAuthRepository
import fr.cklla.cartouche.data.repository.FakeGameDao
import fr.cklla.cartouche.data.repository.fakeGameRepository
import fr.cklla.cartouche.domain.model.AuthUser
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
class StatsViewModelTest {

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
    fun `uiState reflete le backlog courant et se met a jour en direct`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        repository.addGame(Game(title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.TERMINE, userPlaytimeHours = 28))
        repository.addGame(Game(title = "Elden Ring", platform = "PS5", genre = "Action-RPG", status = GameStatus.A_FAIRE))

        val viewModel = StatsViewModel(repository, FakeAuthRepository())
        // uiState est un StateFlow "WhileSubscribed" : il ne collecte le repository
        // qu'une fois observé, comme le ferait la Composable via collectAsStateWithLifecycle.
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(1, state.completedCount)
        assertEquals(2, state.backlogSize)
        assertEquals(28, state.totalHoursPlayed)
        assertEquals(50, state.completionPercent)

        repository.addGame(Game(title = "Celeste", platform = "PC", genre = "Plateforme", status = GameStatus.TERMINE, userPlaytimeHours = 9))
        dispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(2, state.completedCount)
        assertEquals(3, state.backlogSize)
        assertEquals(37, state.totalHoursPlayed)

        collectorJob.cancel()
    }

    @Test
    fun `currentUser reflete l'utilisateur connecte`() = runTest {
        val repository = fakeGameRepository(FakeGameDao())
        val authRepository = FakeAuthRepository(user = AuthUser(uid = "u1", displayName = "Ada"))
        val viewModel = StatsViewModel(repository, authRepository)

        assertEquals("Ada", viewModel.currentUser.value?.displayName)
    }

    @Test
    fun `onSignOutClicked delegue au repository d'auth`() = runTest {
        val repository = fakeGameRepository(FakeGameDao())
        val authRepository = FakeAuthRepository()
        val viewModel = StatsViewModel(repository, authRepository)

        viewModel.onSignOutClicked()

        assertEquals(1, authRepository.signOutCallCount)
    }
}
