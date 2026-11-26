package fr.cklla.cartouche.ui.detail

import androidx.lifecycle.SavedStateHandle
import fr.cklla.cartouche.data.repository.FakeGameDao
import fr.cklla.cartouche.data.repository.fakeGameRepository
import fr.cklla.cartouche.domain.description.DescriptionView
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.domain.model.IgdbGameMatch
import fr.cklla.cartouche.domain.model.Resource
import fr.cklla.cartouche.domain.repository.GameRepository
import fr.cklla.cartouche.ui.navigation.CartoucheDestinations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Déclenchement de la description à l'ouverture de la fiche (une demande par ouverture). */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelDescriptionTest {

    private val dispatcher = StandardTestDispatcher()
    private val ready = DescriptionView.Ready("Original", "Traduction", saved = true)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun addGame(repository: GameRepository, igdbId: Long?): String =
        (repository.addGame(Game(title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.A_FAIRE, igdbId = igdbId)) as Resource.Success).data

    // `igdbLookupAttempted` vrai : la remise à niveau IGDB ne se lance pas et ne modifie pas le jeu.
    private suspend fun addAttempted(repository: GameRepository, igdbId: Long?): String =
        (repository.addGame(Game(title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.A_FAIRE, igdbId = igdbId, igdbLookupAttempted = true)) as Resource.Success).data

    private fun viewModel(repository: GameRepository, id: String, descriptions: FakeDescriptionRepository) = DetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(CartoucheDestinations.DETAIL_ARG_GAME_ID to id)),
        gameRepository = repository,
        igdbPlaytimeRepository = FakeIgdbPlaytimeRepository(),
        igdbUpgradeStore = FakeIgdbUpgradeStore(done = setOf(id)),
        descriptionRepository = descriptions,
    )

    private fun DetailViewModel.collect() = uiState.onEach { }.launchIn(kotlinx.coroutines.CoroutineScope(dispatcher))

    @Test
    fun `jeu du backlog avec id IGDB, la description est demandee une fois et affichee`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, 113112)
        val descriptions = FakeDescriptionRepository { _, _ -> ready }
        val vm = viewModel(repository, id, descriptions)
        val job = vm.collect()
        advanceUntilIdle()

        assertEquals(listOf(id to 113112L), descriptions.calls)
        assertEquals(ready, vm.uiState.value.description)
        job.cancel()
    }

    @Test
    fun `jeu sans id IGDB, aucune demande et aucune section`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, null)
        val descriptions = FakeDescriptionRepository { _, _ -> ready }
        val vm = viewModel(repository, id, descriptions)
        val job = vm.collect()
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, Long?>>(), descriptions.calls)
        assertEquals(DescriptionView.None, vm.uiState.value.description)
        job.cancel()
    }

    @Test
    fun `une panne n est pas retentee en boucle, meme apres des editions de la fiche`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, 113112)
        val descriptions = FakeDescriptionRepository { _, _ -> DescriptionView.Failed }
        val vm = viewModel(repository, id, descriptions)
        val job = vm.collect()
        advanceUntilIdle()

        vm.onRatingSelected(4)
        vm.onNotesChanged("une note")
        vm.onStatusSelected(GameStatus.EN_COURS)
        advanceUntilIdle()

        assertEquals(1, descriptions.calls.size)
        assertEquals(DescriptionView.Failed, vm.uiState.value.description)
        job.cancel()
    }

    @Test
    fun `retirer le jeu retire la section`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, 113112)
        val vm = viewModel(repository, id, FakeDescriptionRepository { _, _ -> ready })
        val job = vm.collect()
        advanceUntilIdle()

        vm.onRemoveGame()
        advanceUntilIdle()

        assertEquals(DescriptionView.None, vm.uiState.value.description)
        job.cancel()
    }

    @Test
    fun `l aperçu d un jeu pas encore ajoute ne demande rien, l ajout declenche la demande`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val descriptions = FakeDescriptionRepository { _, _ -> ready }
        val vm = DetailViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    CartoucheDestinations.DETAIL_APERCU_ARG_IGDB_ID to 42L,
                    CartoucheDestinations.DETAIL_APERCU_ARG_TITLE to "Hollow Knight",
                    CartoucheDestinations.DETAIL_APERCU_ARG_PLATFORM to "PC",
                    CartoucheDestinations.DETAIL_APERCU_ARG_GENRE to "Metroidvania",
                    CartoucheDestinations.DETAIL_APERCU_ARG_YEAR to "2017",
                    CartoucheDestinations.DETAIL_APERCU_ARG_COVER_URL to "",
                ),
            ),
            gameRepository = repository,
            igdbPlaytimeRepository = FakeIgdbPlaytimeRepository(),
            igdbUpgradeStore = FakeIgdbUpgradeStore(),
            descriptionRepository = descriptions,
        )
        val job = vm.collect()
        advanceUntilIdle()
        assertEquals(emptyList<Pair<String, Long?>>(), descriptions.calls)
        assertEquals(DescriptionView.None, vm.uiState.value.description)

        vm.onAddGame()
        advanceUntilIdle()

        assertEquals(1, descriptions.calls.size)
        assertEquals(42L, descriptions.calls.single().second)
        assertEquals(ready, vm.uiState.value.description)
        job.cancel()
    }

    @Test
    fun `l id IGDB obtenu par l enrichissement declenche la demande, une seule fois`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        // Jeu sans id et jamais passé par IGDB : la fiche lit IGDB, qui renvoie l'id, puis la description.
        val id = addGame(repository, null)
        val match = IgdbGameMatch(igdbId = 42, platform = "PC", coverUrl = null, releaseYear = null, playtimeEstimate = null)
        val descriptions = FakeDescriptionRepository { _, _ -> ready }
        val vm = DetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(CartoucheDestinations.DETAIL_ARG_GAME_ID to id)),
            gameRepository = repository,
            igdbPlaytimeRepository = FakeIgdbPlaytimeRepository(match),
            igdbUpgradeStore = FakeIgdbUpgradeStore(),
            descriptionRepository = descriptions,
        )
        val job = vm.collect()
        advanceUntilIdle()
        vm.onRatingSelected(3)
        advanceUntilIdle()

        assertEquals(listOf(id to 42L), descriptions.calls)
        assertEquals(ready, vm.uiState.value.description)
        job.cancel()
    }
}
