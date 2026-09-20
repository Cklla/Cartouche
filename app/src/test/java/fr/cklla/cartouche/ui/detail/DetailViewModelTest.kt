package fr.cklla.cartouche.ui.detail

import androidx.lifecycle.SavedStateHandle
import fr.cklla.cartouche.data.repository.FakeGameDao
import fr.cklla.cartouche.data.repository.fakeGameRepository
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.domain.model.IgdbPlaytimeEstimate
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.repository.GameRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private suspend fun setUpGame(repository: GameRepository): String {
        val result = repository.addGame(
            Game(title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.A_FAIRE),
        )
        return (result as Resource.Success).data
    }

    private fun viewModel(
        repository: GameRepository,
        gameId: String,
        igdbPlaytimeRepository: FakeIgdbPlaytimeRepository = FakeIgdbPlaytimeRepository(),
    ) = DetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(CartoucheDestinations.DETAIL_ARG_GAME_ID to gameId)),
        gameRepository = repository,
        igdbPlaytimeRepository = igdbPlaytimeRepository,
    )

    private fun previewViewModel(
        repository: GameRepository,
        title: String = "Hollow Knight",
        rawgId: Long = 42,
        igdbPlaytimeRepository: FakeIgdbPlaytimeRepository = FakeIgdbPlaytimeRepository(),
    ) = DetailViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(
                CartoucheDestinations.DETAIL_APERCU_ARG_RAWG_ID to rawgId,
                CartoucheDestinations.DETAIL_APERCU_ARG_TITLE to title,
                CartoucheDestinations.DETAIL_APERCU_ARG_PLATFORM to "PC",
                CartoucheDestinations.DETAIL_APERCU_ARG_GENRE to "Metroidvania",
                CartoucheDestinations.DETAIL_APERCU_ARG_YEAR to "2017",
                CartoucheDestinations.DETAIL_APERCU_ARG_COVER_URL to "",
            ),
        ),
        gameRepository = repository,
        igdbPlaytimeRepository = igdbPlaytimeRepository,
    )

    @Test
    fun `changer le statut met a jour le jeu observe`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
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
        val repository = fakeGameRepository(dao)
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
        val repository = fakeGameRepository(dao)
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
    fun `saisir manuellement le temps de jeu met a jour le jeu observe`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onHoursSet(380)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(380, viewModel.uiState.value.game?.userPlaytimeHours)
        collectorJob.cancel()
    }

    @Test
    fun `saisir manuellement puis incrementer part bien de la valeur saisie`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onHoursSet(380)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onHoursIncrement()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(381, viewModel.uiState.value.game?.userPlaytimeHours)
        collectorJob.cancel()
    }

    @Test
    fun `saisir manuellement une valeur negative est ramenee a zero`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onHoursSet(-5)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.game?.userPlaytimeHours)
        collectorJob.cancel()
    }

    @Test
    fun `noter et modifier les notes libres met a jour le jeu`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
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
    fun `recliquer sur l'etoile de la note actuelle efface la note`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onRatingSelected(1)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.game?.rating)

        viewModel.onRatingSelected(null)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.game?.rating)
        collectorJob.cancel()
    }

    @Test
    fun `cocher une plateforme jouee ajoute une plateforme sans retirer les autres`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val result = repository.addGame(
            Game(title = "Trails in the Sky", platform = "PC/PS5/Switch", genre = "RPG", status = GameStatus.EN_COURS),
        )
        val gameId = (result as Resource.Success).data
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPlayedPlatformToggled("Switch")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onPlayedPlatformToggled("PC")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("Switch", "PC"), viewModel.uiState.value.game?.playedPlatforms)
        collectorJob.cancel()
    }

    @Test
    fun `decocher une plateforme jouee la retire de la selection`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val result = repository.addGame(
            Game(
                title = "Trails in the Sky",
                platform = "PC/PS5/Switch",
                genre = "RPG",
                status = GameStatus.EN_COURS,
                playedPlatforms = setOf("Switch"),
            ),
        )
        val gameId = (result as Resource.Success).data
        val viewModel = viewModel(repository, gameId)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onPlayedPlatformToggled("Switch")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptySet<String>(), viewModel.uiState.value.game?.playedPlatforms)
        collectorJob.cancel()
    }

    @Test
    fun `retirer le jeu du backlog fait disparaitre le jeu observe`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
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

    @Test
    fun `ouvrir la fiche declenche une recherche IGDB quand aucun temps estime n'est en cache`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val igdbRepository = FakeIgdbPlaytimeRepository(
            result = IgdbPlaytimeEstimate(hastilyHours = 20, normallyHours = 25, completelyHours = 40),
        )
        val viewModel = viewModel(repository, gameId, igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, igdbRepository.callCount)
        val game = viewModel.uiState.value.game
        assertEquals(20, game?.estimatedPlaytimeHastilyHours)
        assertEquals(25, game?.estimatedPlaytimeNormallyHours)
        assertEquals(40, game?.estimatedPlaytimeCompletelyHours)
        collectorJob.cancel()
    }

    @Test
    fun `un temps estime partiel (IGDB) laisse les champs non renseignes a null`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val igdbRepository = FakeIgdbPlaytimeRepository(
            result = IgdbPlaytimeEstimate(hastilyHours = null, normallyHours = 12, completelyHours = null),
        )
        val viewModel = viewModel(repository, gameId, igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val game = viewModel.uiState.value.game
        assertNull(game?.estimatedPlaytimeHastilyHours)
        assertEquals(12, game?.estimatedPlaytimeNormallyHours)
        assertNull(game?.estimatedPlaytimeCompletelyHours)
        collectorJob.cancel()
    }

    @Test
    fun `ouvrir la fiche ne redemande pas IGDB si un temps estime est deja en cache`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val result = repository.addGame(
            Game(
                title = "Hades",
                platform = "PC",
                genre = "Roguelike",
                status = GameStatus.A_FAIRE,
                estimatedPlaytimeNormallyHours = 20,
            ),
        )
        val gameId = (result as Resource.Success).data
        val igdbRepository = FakeIgdbPlaytimeRepository(
            result = IgdbPlaytimeEstimate(hastilyHours = 1, normallyHours = 999, completelyHours = 1),
        )
        val viewModel = viewModel(repository, gameId, igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, igdbRepository.callCount)
        assertEquals(20, viewModel.uiState.value.game?.estimatedPlaytimeNormallyHours)
        collectorJob.cancel()
    }

    @Test
    fun `un echec IGDB laisse les temps de jeu estimes a null`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val gameId = setUpGame(repository)
        val igdbRepository = FakeIgdbPlaytimeRepository(result = null)
        val viewModel = viewModel(repository, gameId, igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, igdbRepository.callCount)
        val game = viewModel.uiState.value.game
        assertNull(game?.estimatedPlaytimeHastilyHours)
        assertNull(game?.estimatedPlaytimeNormallyHours)
        assertNull(game?.estimatedPlaytimeCompletelyHours)
        collectorJob.cancel()
    }

    @Test
    fun `une fiche ouverte depuis la recherche demarre en apercu, hors backlog`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val viewModel = previewViewModel(repository, title = "Hollow Knight")
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Hollow Knight", state.game?.title)
        assertEquals("", state.game?.id)
        assertFalse(state.isInBacklog)
        assertFalse(state.isLoading)
        collectorJob.cancel()
    }

    @Test
    fun `ajouter depuis l'apercu persiste le jeu et bascule la fiche dans le backlog`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val viewModel = previewViewModel(repository, title = "Hollow Knight")
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddGame()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isInBacklog)
        assertTrue(state.game?.id?.isNotEmpty() == true)
        assertEquals(1, dao.getAllIds().size)
        collectorJob.cancel()
    }

    @Test
    fun `une modification en apercu met a jour la copie locale sans rien persister`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val viewModel = previewViewModel(repository, title = "Hollow Knight")
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onNotesChanged("À essayer")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("À essayer", viewModel.uiState.value.game?.notes)
        assertEquals(0, dao.getAllIds().size)
        collectorJob.cancel()
    }

    @Test
    fun `l'apercu declenche aussi une recherche IGDB`() = runTest {
        val dao = FakeGameDao()
        val repository = fakeGameRepository(dao)
        val igdbRepository = FakeIgdbPlaytimeRepository(
            result = IgdbPlaytimeEstimate(hastilyHours = 10, normallyHours = 27, completelyHours = 60),
        )
        val viewModel = previewViewModel(repository, title = "Hollow Knight", igdbPlaytimeRepository = igdbRepository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, igdbRepository.callCount)
        assertEquals(27, viewModel.uiState.value.game?.estimatedPlaytimeNormallyHours)
        assertEquals(0, dao.getAllIds().size)
        collectorJob.cancel()
    }
}
