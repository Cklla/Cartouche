package fr.cklla.cartouche.ui.stats

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsCalculationsTest {

    @Test
    fun `backlog vide renvoie des stats a zero`() {
        val stats = computeStats(emptyList())

        assertEquals(0, stats.completedCount)
        assertEquals(0, stats.backlogSize)
        assertEquals(0, stats.totalHoursPlayed)
        assertEquals(0, stats.completionPercent)
        assertEquals(GameStatus.entries.associateWith { 0 }, stats.countsByStatus)
    }

    @Test
    fun `compte les jeux termines, la taille du backlog et les heures cumulees`() {
        val games = listOf(
            Game(title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.TERMINE, userPlaytimeHours = 28),
            Game(title = "Celeste", platform = "PC", genre = "Plateforme", status = GameStatus.TERMINE, userPlaytimeHours = 9),
            Game(title = "Elden Ring", platform = "PS5", genre = "Action-RPG", status = GameStatus.A_FAIRE, userPlaytimeHours = 0),
            Game(title = "Zelda", platform = "Switch", genre = "Aventure", status = GameStatus.EN_COURS, userPlaytimeHours = 34),
        )

        val stats = computeStats(games)

        assertEquals(2, stats.completedCount)
        assertEquals(4, stats.backlogSize)
        assertEquals(71, stats.totalHoursPlayed)
        assertEquals(1, stats.countsByStatus[GameStatus.A_FAIRE])
        assertEquals(1, stats.countsByStatus[GameStatus.EN_COURS])
        assertEquals(2, stats.countsByStatus[GameStatus.TERMINE])
        assertEquals(0, stats.countsByStatus[GameStatus.ABANDONNE])
    }

    @Test
    fun `pourcentage de completion arrondi comme dans le prototype`() {
        // 4 termines sur 9 -> 44,44...% arrondi a 44 (exemple de la maquette).
        val games = List(4) { termineGame() } + List(5) { aFaireGame() }

        assertEquals(44, computeStats(games).completionPercent)
    }

    @Test
    fun `pourcentage arrondi au superieur a partir de 0,5`() {
        // 1 termine sur 8 -> 12,5% doit arrondir a 13, comme Math_round en JS.
        val games = List(1) { termineGame() } + List(7) { aFaireGame() }

        assertEquals(13, computeStats(games).completionPercent)
    }

    private fun termineGame() = Game(title = "Jeu", platform = "PC", genre = "Genre", status = GameStatus.TERMINE)
    private fun aFaireGame() = Game(title = "Jeu", platform = "PC", genre = "Genre", status = GameStatus.A_FAIRE)
}
