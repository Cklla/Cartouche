package fr.cklla.cartouche.ui.stats

import androidx.lifecycle.SavedStateHandle
import fr.cklla.cartouche.data.repository.FakeGameDao
import fr.cklla.cartouche.data.repository.fakeGameRepository
import fr.cklla.cartouche.data.repository.toEntity
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.ui.navigation.CartoucheDestinations
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

/**
 * Les jeux sont insérés directement dans `FakeGameDao` : `GameRepositoryImpl.addGame` recalcule
 * `completedAt` / `abandonedAt` à l'instant présent, donc une date passée ne survivrait pas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecapStoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val dao = FakeGameDao()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(year: Int = 2026) = RecapStoryViewModel(
        SavedStateHandle(mapOf(CartoucheDestinations.RECAP_ARG_YEAR to year)),
        fakeGameRepository(dao),
    )

    private fun dateIn(year: Int): Long =
        ZonedDateTime.of(year, 6, 15, 20, 30, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun game(
        id: String,
        rating: Int? = null,
        status: GameStatus = GameStatus.TERMINE,
        year: Int = 2026,
    ) = Game(
        id = id,
        title = id,
        platform = "PC",
        genre = "Aventure",
        status = status,
        rating = rating,
        completedAt = if (status == GameStatus.TERMINE) dateIn(year) else null,
        abandonedAt = if (status == GameStatus.ABANDONNE) dateIn(year) else null,
    )

    private fun RecapStoryViewModel.keys() = uiState.value.slides.map { it.key }

    @Test
    fun `l'annee de la route est lue depuis le SavedStateHandle`() = runTest(dispatcher) {
        dao.insert(game("de-2026", year = 2026).toEntity())
        dao.insert(game("de-2025", year = 2025).toEntity())
        val viewModel = viewModel(year = 2025)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(2025, viewModel.year)
        val mosaic = viewModel.uiState.value.slides.filterIsInstance<RecapSlide.Mosaic>().single()
        assertEquals(listOf("de-2025"), mosaic.games.map { it.id })
    }

    @Test
    fun `l'etat est en chargement puis sans slide quand rien n'a ete termine`() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.isLoading)

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.slides.isEmpty())
    }

    @Test
    fun `une note modifiee met les slides a jour en direct`() = runTest(dispatcher) {
        dao.insert(game("hades", rating = 3).toEntity())
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertFalse("favorites" in viewModel.keys())

        dao.insert(game("hades", rating = 5).toEntity())
        advanceUntilIdle()

        val slide = viewModel.uiState.value.slides.filterIsInstance<RecapSlide.Favorites>().single()
        assertEquals(listOf("hades"), slide.games.map { it.id })
    }

    @Test
    fun `un changement de statut met le total et la mosaique a jour en direct`() = runTest(dispatcher) {
        dao.insert(game("hades").toEntity())
        dao.insert(game("celeste", status = GameStatus.EN_COURS).toEntity())
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, (viewModel.uiState.value.slides.first() as RecapSlide.Total).completedCount)

        dao.insert(game("celeste").toEntity())
        advanceUntilIdle()
        assertEquals(2, (viewModel.uiState.value.slides.first() as RecapSlide.Total).completedCount)
        assertEquals(2, viewModel.uiState.value.slides.filterIsInstance<RecapSlide.Mosaic>().single().games.size)

        dao.insert(game("celeste", status = GameStatus.ABANDONNE).toEntity())
        advanceUntilIdle()
        val total = viewModel.uiState.value.slides.first() as RecapSlide.Total
        assertEquals(1, total.completedCount)
        assertEquals(1, total.abandonedCount)

        dao.insert(game("hades", status = GameStatus.A_FAIRE).toEntity())
        dao.insert(game("celeste", status = GameStatus.A_FAIRE).toEntity())
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.slides.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
