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

    // Milieu d'année en UTC : hors de portée d'un changement de fuseau horaire local qui ferait
    // basculer la date sur l'année voisine (au plus ±14h autour de l'UTC).
    private val completedIn2023 = 1_688_169_600_000L // 2023-07-01T00:00:00Z
    private val completedIn2024 = 1_719_792_000_000L // 2024-07-01T00:00:00Z

    @Test
    fun `sans annee selectionnee, availableYears liste les annees disponibles`() {
        val games = listOf(
            Game(title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.TERMINE, completedAt = completedIn2024),
            Game(title = "Celeste", platform = "PC", genre = "Plateforme", status = GameStatus.TERMINE, completedAt = completedIn2023),
            Game(title = "Elden Ring", platform = "PS5", genre = "Action-RPG", status = GameStatus.A_FAIRE),
        )

        assertEquals(listOf(2024, 2023), computeStats(games).availableYears)
    }

    @Test
    fun `avec une annee selectionnee, ne compte que les jeux termines cette annee-la`() {
        val hades = Game(title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.TERMINE, userPlaytimeHours = 28, completedAt = completedIn2024)
        val celeste = Game(title = "Celeste", platform = "PC", genre = "Plateforme", status = GameStatus.TERMINE, userPlaytimeHours = 9, completedAt = completedIn2023)
        val eldenRing = Game(title = "Elden Ring", platform = "PS5", genre = "Action-RPG", status = GameStatus.A_FAIRE, userPlaytimeHours = 100)
        val games = listOf(hades, celeste, eldenRing)

        val stats = computeStats(games, selectedYear = 2024)

        assertEquals(1, stats.completedCount)
        assertEquals(28, stats.totalHoursPlayed)
        assertEquals(2024, stats.selectedYear)
        assertEquals(1, stats.countsByStatus[GameStatus.TERMINE])
        assertEquals(0, stats.countsByStatus[GameStatus.A_FAIRE])
    }

    @Test
    fun `avec une annee selectionnee, compte aussi les jeux abandonnes cette annee-la`() {
        val hades = Game(title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.TERMINE, userPlaytimeHours = 28, completedAt = completedIn2024)
        val cyberpunk = Game(title = "Cyberpunk 2077", platform = "PC", genre = "Action-RPG", status = GameStatus.ABANDONNE, abandonedAt = completedIn2024)
        val celeste = Game(title = "Celeste", platform = "PC", genre = "Plateforme", status = GameStatus.ABANDONNE, abandonedAt = completedIn2023)
        val games = listOf(hades, cyberpunk, celeste)

        val stats = computeStats(games, selectedYear = 2024)

        assertEquals(1, stats.completedCount)
        assertEquals(1, stats.countsByStatus[GameStatus.TERMINE])
        assertEquals(1, stats.countsByStatus[GameStatus.ABANDONNE])
        // Les heures de jeu ne portent que sur les jeux terminés, pas sur les abandonnés.
        assertEquals(28, stats.totalHoursPlayed)
    }

    @Test
    fun `availableYears est l'union des annees de completion et d'abandon`() {
        val hades = Game(title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.TERMINE, completedAt = completedIn2024)
        val cyberpunk = Game(title = "Cyberpunk 2077", platform = "PC", genre = "Action-RPG", status = GameStatus.ABANDONNE, abandonedAt = completedIn2023)

        assertEquals(listOf(2024, 2023), computeStats(listOf(hades, cyberpunk)).availableYears)
    }
}
