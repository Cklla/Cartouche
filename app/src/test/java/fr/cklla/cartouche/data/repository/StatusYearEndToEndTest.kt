package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.ui.bibliotheque.BacklogFilter
import fr.cklla.cartouche.ui.bibliotheque.availableYearsFor
import fr.cklla.cartouche.ui.bibliotheque.filterGames
import fr.cklla.cartouche.ui.stats.computeStats
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Chemin complet : `setStatusYear` écrit dans Room, puis les mêmes fonctions que la Bibliothèque
 * (filtre, chips d'année) et l'écran Stats relisent l'année de ce jeu. Les jeux sont insérés
 * directement dans [FakeGameDao] (voir `GameRepositoryStatusYearTest`).
 */
class StatusYearEndToEndTest {

    private val currentYear = LocalDate.now().year
    private val pastYear = currentYear - 2
    private val now = System.currentTimeMillis()

    private val base = Game(title = "", platform = "PC", genre = "RPG", status = GameStatus.TERMINE)

    @Test
    fun `un jeu Termine reclasse apparait sous la nouvelle annee dans la Bibliotheque et les stats`() = runTest {
        val dao = FakeGameDao()
        dao.insert(base.copy(id = "hades", title = "Hades", completedAt = now, userPlaytimeHours = 40).toEntity())
        dao.insert(base.copy(id = "celeste", title = "Celeste", completedAt = now, userPlaytimeHours = 10).toEntity())
        val repository = fakeGameRepository(dao)
        assertEquals(listOf(currentYear), availableYearsFor(BacklogFilter.TERMINE, repository.observeGames().first()))

        repository.setStatusYear("hades", pastYear)

        val games = repository.observeGames().first()
        assertEquals(listOf(currentYear, pastYear), availableYearsFor(BacklogFilter.TERMINE, games))
        assertEquals(listOf("Hades"), filterGames(games, BacklogFilter.TERMINE, selectedYear = pastYear).map { it.title })
        assertEquals(listOf("Celeste"), filterGames(games, BacklogFilter.TERMINE, selectedYear = currentYear).map { it.title })

        val statsPast = computeStats(games, selectedYear = pastYear)
        assertEquals(1, statsPast.completedCount)
        assertEquals(40, statsPast.totalHoursPlayed)
        assertEquals(1, statsPast.countsByStatus.getValue(GameStatus.TERMINE))
        val statsCurrent = computeStats(games, selectedYear = currentYear)
        assertEquals(1, statsCurrent.completedCount)
        assertEquals(10, statsCurrent.totalHoursPlayed)
        assertEquals(2, computeStats(games).completedCount)
    }

    @Test
    fun `un jeu Termine sans annee en recoit une et entre dans les chips et les stats`() = runTest {
        val dao = FakeGameDao()
        // Terminé avant la migration qui a introduit completedAt : aucune année.
        dao.insert(base.copy(id = "ancien", title = "Chrono Trigger", completedAt = null, userPlaytimeHours = 25).toEntity())
        val repository = fakeGameRepository(dao)
        val before = repository.observeGames().first()
        assertEquals(emptyList<Int>(), availableYearsFor(BacklogFilter.TERMINE, before))
        assertEquals(emptyList<Int>(), computeStats(before).availableYears)

        repository.setStatusYear("ancien", pastYear)

        val games = repository.observeGames().first()
        assertEquals(listOf(pastYear), availableYearsFor(BacklogFilter.TERMINE, games))
        assertEquals(listOf("Chrono Trigger"), filterGames(games, BacklogFilter.TERMINE, selectedYear = pastYear).map { it.title })
        assertEquals(listOf(pastYear), computeStats(games).availableYears)
        val stats = computeStats(games, selectedYear = pastYear)
        assertEquals(1, stats.completedCount)
        assertEquals(25, stats.totalHoursPlayed)
    }

    @Test
    fun `un jeu Abandonne sans annee en recoit une et entre dans les chips et les stats`() = runTest {
        val dao = FakeGameDao()
        dao.insert(base.copy(id = "ancien", title = "Fable", status = GameStatus.ABANDONNE, abandonedAt = null).toEntity())
        val repository = fakeGameRepository(dao)
        assertEquals(emptyList<Int>(), availableYearsFor(BacklogFilter.ABANDONNE, repository.observeGames().first()))

        repository.setStatusYear("ancien", pastYear)

        val games = repository.observeGames().first()
        assertEquals(listOf(pastYear), availableYearsFor(BacklogFilter.ABANDONNE, games))
        assertEquals(listOf("Fable"), filterGames(games, BacklogFilter.ABANDONNE, selectedYear = pastYear).map { it.title })
        // Aucun jeu Terminé : l'année d'abandon n'apparaît pas sous l'onglet Terminé.
        assertEquals(emptyList<Int>(), availableYearsFor(BacklogFilter.TERMINE, games))
        val stats = computeStats(games, selectedYear = pastYear)
        assertEquals(1, stats.countsByStatus.getValue(GameStatus.ABANDONNE))
        assertEquals(0, stats.countsByStatus.getValue(GameStatus.TERMINE))
    }
}
