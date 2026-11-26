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
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Description de la fiche : lecture gratuite à l'ouverture, une seule demande de traduction au premier déroulement. */
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
    fun `fiche ouverte sans derouler, aucune demande de traduction, seulement une lecture`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, 113112)
        val descriptions = FakeDescriptionRepository { _, _ -> ready }
        val vm = viewModel(repository, id, descriptions)
        val job = vm.collect()
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, Long?>>(), descriptions.calls)
        assertEquals(listOf(id to 113112L), descriptions.peeks)
        assertEquals(DescriptionView.Collapsed, vm.uiState.value.description)
        assertFalse(vm.uiState.value.descriptionExpanded)
        job.cancel()
    }

    @Test
    fun `le deroulement declenche une demande, le texte est affiche`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, 113112)
        val descriptions = FakeDescriptionRepository { _, _ -> ready }
        val vm = viewModel(repository, id, descriptions)
        val job = vm.collect()
        advanceUntilIdle()

        vm.onDescriptionToggled()
        advanceUntilIdle()

        assertEquals(listOf(id to 113112L), descriptions.calls)
        assertEquals(ready, vm.uiState.value.description)
        assertTrue(vm.uiState.value.descriptionExpanded)
        job.cancel()
    }

    @Test
    fun `refermer puis rouvrir ne redemande rien`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, 113112)
        val descriptions = FakeDescriptionRepository { _, _ -> ready }
        val vm = viewModel(repository, id, descriptions)
        val job = vm.collect()
        advanceUntilIdle()

        vm.onDescriptionToggled()
        advanceUntilIdle()
        vm.onDescriptionToggled()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.descriptionExpanded)
        vm.onDescriptionToggled()
        advanceUntilIdle()

        assertEquals(1, descriptions.calls.size)
        assertTrue(vm.uiState.value.descriptionExpanded)
        assertEquals(ready, vm.uiState.value.description)
        job.cancel()
    }

    @Test
    fun `description deja connue, montree au deroulement sans aucune demande`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, 113112)
        val descriptions = FakeDescriptionRepository(peeker = { _, _ -> ready })
        val vm = viewModel(repository, id, descriptions)
        val job = vm.collect()
        advanceUntilIdle()
        assertEquals(ready, vm.uiState.value.description)

        vm.onDescriptionToggled()
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, Long?>>(), descriptions.calls)
        assertTrue(vm.uiState.value.descriptionExpanded)
        job.cancel()
    }

    @Test
    fun `IGDB sans description deja connu, pas de section et le deroulement n y change rien`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, 113112)
        val descriptions = FakeDescriptionRepository(peeker = { _, _ -> DescriptionView.None })
        val vm = viewModel(repository, id, descriptions)
        val job = vm.collect()
        advanceUntilIdle()

        vm.onDescriptionToggled()
        advanceUntilIdle()

        assertEquals(DescriptionView.None, vm.uiState.value.description)
        assertFalse(vm.uiState.value.descriptionExpanded)
        assertEquals(emptyList<Pair<String, Long?>>(), descriptions.calls)
        job.cancel()
    }

    @Test
    fun `jeu sans id IGDB, aucune lecture ni demande, meme deroule`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, null)
        val descriptions = FakeDescriptionRepository { _, _ -> ready }
        val vm = viewModel(repository, id, descriptions)
        val job = vm.collect()
        advanceUntilIdle()
        vm.onDescriptionToggled()
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, Long?>>(), descriptions.peeks)
        assertEquals(emptyList<Pair<String, Long?>>(), descriptions.calls)
        assertEquals(DescriptionView.None, vm.uiState.value.description)
        assertFalse(vm.uiState.value.descriptionExpanded)
        job.cancel()
    }

    @Test
    fun `une panne n est pas retentee en boucle, ni par les editions ni en refermant et rouvrant`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, 113112)
        val descriptions = FakeDescriptionRepository { _, _ -> DescriptionView.Failed }
        val vm = viewModel(repository, id, descriptions)
        val job = vm.collect()
        advanceUntilIdle()
        vm.onDescriptionToggled()
        advanceUntilIdle()

        vm.onRatingSelected(4)
        vm.onNotesChanged("une note")
        vm.onStatusSelected(GameStatus.EN_COURS)
        advanceUntilIdle()
        vm.onDescriptionToggled() // referme
        advanceUntilIdle()
        assertFalse(vm.uiState.value.descriptionExpanded)
        vm.onDescriptionToggled() // rouvre
        advanceUntilIdle()

        assertTrue(vm.uiState.value.descriptionExpanded)
        assertEquals(1, descriptions.calls.size)
        assertEquals(DescriptionView.Failed, vm.uiState.value.description)
        job.cancel()
    }

    @Test
    fun `refermer pendant la demande ne l annule pas, la reponse arrive`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, 113112)
        val gate = CompletableDeferred<DescriptionView>()
        val descriptions = FakeDescriptionRepository { _, _ -> gate.await() }
        val vm = viewModel(repository, id, descriptions)
        val job = vm.collect()
        advanceUntilIdle()

        vm.onDescriptionToggled()
        advanceUntilIdle()
        assertEquals(DescriptionView.Loading, vm.uiState.value.description)
        vm.onDescriptionToggled() // on referme pendant la traduction
        advanceUntilIdle()
        gate.complete(ready)
        advanceUntilIdle()

        assertEquals(1, descriptions.calls.size)
        assertEquals(ready, vm.uiState.value.description)
        assertFalse(vm.uiState.value.descriptionExpanded)
        job.cancel()
    }

    @Test
    fun `jeu retire pendant la demande, la reponse tardive n est jamais affichee et la section repart repliee`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, 113112)
        val gate = CompletableDeferred<DescriptionView>()
        val descriptions = FakeDescriptionRepository { _, _ -> gate.await() }
        val vm = viewModel(repository, id, descriptions)
        val job = vm.collect()
        advanceUntilIdle()
        vm.onDescriptionToggled()
        advanceUntilIdle()

        vm.onRemoveGame()
        advanceUntilIdle()
        gate.complete(ready)
        advanceUntilIdle()

        assertEquals(DescriptionView.None, vm.uiState.value.description)
        assertFalse(vm.uiState.value.descriptionExpanded)
        job.cancel()
    }

    @Test
    fun `jeu retire pendant l attente du deroulement, la section disparait sans qu on ait deroule`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, 113112)
        val descriptions = FakeDescriptionRepository { _, _ -> ready }
        val vm = viewModel(repository, id, descriptions)
        val job = vm.collect()
        advanceUntilIdle()
        assertEquals(DescriptionView.Collapsed, vm.uiState.value.description)

        // La clé change (jeu retiré) alors que l'ancienne attend encore son déroulement : la nouvelle
        // est traitée quand même, rien n'est demandé.
        vm.onRemoveGame()
        advanceUntilIdle()

        assertEquals(DescriptionView.None, vm.uiState.value.description)
        assertEquals(emptyList<Pair<String, Long?>>(), descriptions.calls)
        job.cancel()
    }

    @Test
    fun `section repliee des l ouverture, pendant que la lecture est en cours`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, 113112)
        val gate = CompletableDeferred<DescriptionView>()
        val descriptions = FakeDescriptionRepository(peeker = { _, _ -> gate.await() })
        val vm = viewModel(repository, id, descriptions)
        val job = vm.collect()
        advanceUntilIdle()

        assertEquals(DescriptionView.Collapsed, vm.uiState.value.description)
        gate.complete(ready)
        advanceUntilIdle()
        assertEquals(ready, vm.uiState.value.description)
        assertEquals(emptyList<Pair<String, Long?>>(), descriptions.calls)
        job.cancel()
    }

    @Test
    fun `deroule pendant la lecture, la demande part apres la lecture si rien n est connu`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, 113112)
        val gate = CompletableDeferred<DescriptionView>()
        val descriptions = FakeDescriptionRepository(peeker = { _, _ -> gate.await() }, responder = { _, _ -> ready })
        val vm = viewModel(repository, id, descriptions)
        val job = vm.collect()
        advanceUntilIdle()

        vm.onDescriptionToggled()
        advanceUntilIdle()
        assertEquals(emptyList<Pair<String, Long?>>(), descriptions.calls)
        gate.complete(DescriptionView.Collapsed)
        advanceUntilIdle()

        assertEquals(1, descriptions.calls.size)
        assertEquals(ready, vm.uiState.value.description)
        job.cancel()
    }

    @Test
    fun `jeu retire pendant la lecture, la lecture tardive n est jamais affichee`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, 113112)
        val gate = CompletableDeferred<DescriptionView>()
        val descriptions = FakeDescriptionRepository(peeker = { _, _ -> gate.await() })
        val vm = viewModel(repository, id, descriptions)
        val job = vm.collect()
        advanceUntilIdle()

        vm.onRemoveGame()
        advanceUntilIdle()
        gate.complete(ready)
        advanceUntilIdle()

        assertEquals(DescriptionView.None, vm.uiState.value.description)
        job.cancel()
    }

    @Test
    fun `retirer le jeu retire la section et la referme`() = runTest(dispatcher) {
        val repository = fakeGameRepository(FakeGameDao())
        val id = addAttempted(repository, 113112)
        val vm = viewModel(repository, id, FakeDescriptionRepository { _, _ -> ready })
        val job = vm.collect()
        advanceUntilIdle()
        vm.onDescriptionToggled()
        advanceUntilIdle()

        vm.onRemoveGame()
        advanceUntilIdle()

        assertEquals(DescriptionView.None, vm.uiState.value.description)
        assertFalse(vm.uiState.value.descriptionExpanded)
        job.cancel()
    }

    @Test
    fun `l aperçu d un jeu pas encore ajoute ne lit ni ne demande rien, apres l ajout seul le deroulement demande`() = runTest(dispatcher) {
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
        assertEquals(emptyList<Pair<String, Long?>>(), descriptions.peeks)
        assertEquals(emptyList<Pair<String, Long?>>(), descriptions.calls)
        assertEquals(DescriptionView.None, vm.uiState.value.description)

        vm.onAddGame()
        advanceUntilIdle()
        // Ajouté au backlog : la section apparaît repliée, rien n'est encore demandé.
        assertEquals(emptyList<Pair<String, Long?>>(), descriptions.calls)
        assertEquals(DescriptionView.Collapsed, vm.uiState.value.description)

        vm.onDescriptionToggled()
        advanceUntilIdle()

        assertEquals(1, descriptions.calls.size)
        assertEquals(42L, descriptions.calls.single().second)
        assertEquals(ready, vm.uiState.value.description)
        job.cancel()
    }

    @Test
    fun `l id IGDB obtenu par l enrichissement fait apparaitre la section repliee, une seule demande au deroulement`() = runTest(dispatcher) {
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
        assertEquals(emptyList<Pair<String, Long?>>(), descriptions.calls)
        assertEquals(listOf(id to 42L), descriptions.peeks)

        vm.onDescriptionToggled()
        advanceUntilIdle()

        assertEquals(listOf(id to 42L), descriptions.calls)
        assertEquals(ready, vm.uiState.value.description)
        job.cancel()
    }
}
