package fr.cklla.cartouche.ui.detail

import androidx.lifecycle.SavedStateHandle
import fr.cklla.cartouche.data.repository.FakeGameDao
import fr.cklla.cartouche.data.repository.fakeGameRepository
import fr.cklla.cartouche.data.repository.toEntity
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.domain.repository.GameRepository
import fr.cklla.cartouche.domain.util.timestampForYear
import fr.cklla.cartouche.ui.abandonedYear
import fr.cklla.cartouche.ui.completedYear
import fr.cklla.cartouche.ui.navigation.CartoucheDestinations
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

/**
 * Ligne « Terminé en … » / « Abandonné en … » de la fiche et copie de travail du ViewModel. Les
 * jeux sont insérés directement dans [FakeGameDao] : `addGame` daterait un jeu déjà Terminé à
 * l'instant présent, ce qui empêcherait de partir d'une année passée ou d'une date absente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelStatusYearTest {

    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.systemDefault()
    private val currentYear get() = LocalDate.now().year

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // `igdbLookupAttempted = true` : pas de recherche IGDB à l'ouverture, qui réécrirait le jeu.
    private val hades = Game(
        id = "hades",
        title = "Hades",
        platform = "PC",
        genre = "Roguelike",
        status = GameStatus.TERMINE,
        releaseYear = 2020,
        igdbLookupAttempted = true,
    )

    // Un 1er juillet à midi : sans ambiguïté de fuseau, comme ce que le repository écrit.
    private fun inYear(year: Int) =
        timestampForYear(year, currentTimestamp = null, releaseYear = null, nowMillis = System.currentTimeMillis(), zone = zone)!!

    private fun viewModel(repository: GameRepository, gameId: String = "hades") = DetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(CartoucheDestinations.DETAIL_ARG_GAME_ID to gameId)),
        gameRepository = repository,
        igdbPlaytimeRepository = FakeIgdbPlaytimeRepository(),
    )

    private suspend fun GameRepository.stored(id: String = "hades"): Game = observeGame(id).first()!!

    @Test
    fun `un jeu Termine affiche son annee de fin`() = runTest {
        val dao = FakeGameDao().apply { insert(hades.copy(completedAt = inYear(2024)).toEntity()) }
        val viewModel = viewModel(fakeGameRepository(dao))
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.canEditStatusYear)
        assertEquals(2024, state.statusYear)
        collectorJob.cancel()
    }

    @Test
    fun `un jeu Termine sans date a une annee inconnue mais modifiable`() = runTest {
        val dao = FakeGameDao().apply { insert(hades.copy(completedAt = null).toEntity()) }
        val viewModel = viewModel(fakeGameRepository(dao))
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.canEditStatusYear)
        assertNull(state.statusYear)
        collectorJob.cancel()
    }

    @Test
    fun `un jeu Abandonne affiche son annee d abandon`() = runTest {
        val dao = FakeGameDao().apply {
            insert(hades.copy(status = GameStatus.ABANDONNE, abandonedAt = inYear(2023)).toEntity())
        }
        val viewModel = viewModel(fakeGameRepository(dao))
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.canEditStatusYear)
        assertEquals(2023, state.statusYear)
        collectorJob.cancel()
    }

    @Test
    fun `la ligne est absente hors Termine et Abandonne`() = runTest {
        listOf(GameStatus.A_FAIRE, GameStatus.EN_COURS).forEach { status ->
            val dao = FakeGameDao().apply { insert(hades.copy(status = status).toEntity()) }
            val viewModel = viewModel(fakeGameRepository(dao))
            val collectorJob = launch { viewModel.uiState.collect {} }
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(status.name, state.canEditStatusYear)
            assertNull(status.name, state.statusYear)
            assertTrue(status.name, state.statusYearChoices.isEmpty())
            collectorJob.cancel()
        }
    }

    @Test
    fun `la ligne est absente pour l apercu d un jeu pas encore dans le backlog`() = runTest {
        val repository = fakeGameRepository(FakeGameDao())
        val viewModel = DetailViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    CartoucheDestinations.DETAIL_APERCU_ARG_RAWG_ID to 42L,
                    CartoucheDestinations.DETAIL_APERCU_ARG_TITLE to "Hollow Knight",
                    CartoucheDestinations.DETAIL_APERCU_ARG_PLATFORM to "PC",
                    CartoucheDestinations.DETAIL_APERCU_ARG_GENRE to "Metroidvania",
                    CartoucheDestinations.DETAIL_APERCU_ARG_YEAR to "2017",
                    CartoucheDestinations.DETAIL_APERCU_ARG_COVER_URL to "",
                ),
            ),
            gameRepository = repository,
            igdbPlaytimeRepository = FakeIgdbPlaytimeRepository(),
        )
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isInBacklog)
        assertFalse(state.canEditStatusYear)
        assertTrue(state.statusYearChoices.isEmpty())
        // Même un statut daté ne suffit pas sans id de backlog.
        assertFalse(DetailUiState(game = hades.copy(id = "")).canEditStatusYear)
        collectorJob.cancel()
    }

    @Test
    fun `les annees proposees vont de l annee en cours a l annee de sortie`() = runTest {
        val dao = FakeGameDao().apply { insert(hades.copy(completedAt = inYear(2024)).toEntity()) }
        val viewModel = viewModel(fakeGameRepository(dao))
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals((currentYear downTo 2020).toList(), viewModel.uiState.value.statusYearChoices)
        collectorJob.cancel()
    }

    @Test
    fun `choisir une annee la persiste et l affiche`() = runTest {
        val dao = FakeGameDao().apply { insert(hades.copy(completedAt = null).toEntity()) }
        val repository = fakeGameRepository(dao)
        val viewModel = viewModel(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusYearSelected(2022)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2022, viewModel.uiState.value.statusYear)
        assertEquals(2022, completedYear(repository.stored()))
        collectorJob.cancel()
    }

    @Test
    fun `choisir une annee d abandon ecrit abandonedAt`() = runTest {
        val dao = FakeGameDao().apply {
            insert(hades.copy(status = GameStatus.ABANDONNE, abandonedAt = null).toEntity())
        }
        val repository = fakeGameRepository(dao)
        val viewModel = viewModel(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusYearSelected(2021)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2021, viewModel.uiState.value.statusYear)
        assertEquals(2021, abandonedYear(repository.stored()))
        assertNull(repository.stored().completedAt)
        collectorJob.cancel()
    }

    @Test
    fun `choisir une annee est ignore hors Termine et Abandonne`() = runTest {
        val dao = FakeGameDao().apply { insert(hades.copy(status = GameStatus.EN_COURS).toEntity()) }
        val repository = fakeGameRepository(dao)
        val viewModel = viewModel(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusYearSelected(2022)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(repository.stored().completedAt)
        assertNull(repository.stored().abandonedAt)
        collectorJob.cancel()
    }

    @Test
    fun `une edition apres le choix de l annee ne ramene pas l ancienne valeur`() = runTest {
        val dao = FakeGameDao().apply { insert(hades.copy(completedAt = null).toEntity()) }
        val repository = fakeGameRepository(dao)
        val viewModel = viewModel(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusYearSelected(2022)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onRatingSelected(4)
        viewModel.onNotesChanged("Fini en une nuit")
        viewModel.onHoursSet(30)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2022, viewModel.uiState.value.statusYear)
        val persisted = repository.stored()
        assertEquals(2022, completedYear(persisted))
        assertEquals(4, persisted.rating)
        assertEquals("Fini en une nuit", persisted.notes)
        collectorJob.cancel()
    }

    @Test
    fun `une edition lancee juste avant le choix de l annee ne l ecrase pas`() = runTest {
        val dao = FakeGameDao().apply { insert(hades.copy(completedAt = null).toEntity()) }
        val repository = fakeGameRepository(dao)
        val viewModel = viewModel(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        // Sans attendre entre les deux : la copie de travail de l'écran est celle d'avant le choix.
        viewModel.onRatingSelected(3)
        viewModel.onStatusYearSelected(2022)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2022, viewModel.uiState.value.statusYear)
        assertEquals(2022, completedYear(repository.stored()))
        assertEquals(3, repository.stored().rating)
        collectorJob.cancel()
    }

    @Test
    fun `passer de A faire a Termine affiche tout de suite l annee en cours`() = runTest {
        val dao = FakeGameDao().apply { insert(hades.copy(status = GameStatus.A_FAIRE).toEntity()) }
        val viewModel = viewModel(fakeGameRepository(dao))
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusSelected(GameStatus.TERMINE)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.canEditStatusYear)
        assertEquals(currentYear, state.statusYear)
        collectorJob.cancel()
    }

    @Test
    fun `passer de Termine a Abandonne affiche l annee en cours pour l abandon`() = runTest {
        val dao = FakeGameDao().apply { insert(hades.copy(completedAt = inYear(2022)).toEntity()) }
        val viewModel = viewModel(fakeGameRepository(dao))
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusSelected(GameStatus.ABANDONNE)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(currentYear, state.statusYear)
        assertNull(state.game?.completedAt)
        collectorJob.cancel()
    }

    @Test
    fun `sortir de Termine masque la ligne et perd l annee choisie`() = runTest {
        val dao = FakeGameDao().apply { insert(hades.copy(completedAt = inYear(2022)).toEntity()) }
        val repository = fakeGameRepository(dao)
        val viewModel = viewModel(repository)
        val collectorJob = launch { viewModel.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusSelected(GameStatus.EN_COURS)
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canEditStatusYear)
        assertNull(repository.stored().completedAt)

        viewModel.onStatusSelected(GameStatus.TERMINE)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(currentYear, viewModel.uiState.value.statusYear)
        collectorJob.cancel()
    }
}
