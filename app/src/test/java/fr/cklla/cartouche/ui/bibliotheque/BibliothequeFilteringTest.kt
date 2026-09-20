package fr.cklla.cartouche.ui.bibliotheque

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.ui.abandonedYear
import fr.cklla.cartouche.ui.availableAbandonedYears
import fr.cklla.cartouche.ui.availableCompletedYears
import fr.cklla.cartouche.ui.completedYear
import org.junit.Assert.assertEquals
import org.junit.Test

class BibliothequeFilteringTest {

    private val hades = Game(id = "1", title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.TERMINE)
    private val eldenRing = Game(id = "2", title = "Elden Ring", platform = "PS5", genre = "Action-RPG", status = GameStatus.A_FAIRE)
    private val zelda = Game(id = "3", title = "Zelda", platform = "Switch", genre = "Aventure", status = GameStatus.EN_COURS)
    private val games = listOf(hades, eldenRing, zelda)

    // Milieu d'année en UTC : hors de portée d'un changement de fuseau horaire local qui ferait
    // basculer la date sur l'année voisine (au plus ±14h autour de l'UTC).
    private val completedIn2023 = 1_688_169_600_000L // 2023-07-01T00:00:00Z
    private val completedIn2024 = 1_719_792_000_000L // 2024-07-01T00:00:00Z

    @Test
    fun `filtre TOUS renvoie tous les jeux`() {
        assertEquals(games, filterGames(games, BacklogFilter.TOUS))
    }

    @Test
    fun `filtre par statut ne renvoie que les jeux correspondants`() {
        assertEquals(listOf(hades), filterGames(games, BacklogFilter.TERMINE))
        assertEquals(listOf(eldenRing), filterGames(games, BacklogFilter.A_FAIRE))
        assertEquals(emptyList<Game>(), filterGames(games, BacklogFilter.ABANDONNE))
    }

    @Test
    fun `countByFilter compte chaque statut et TOUS compte l'ensemble`() {
        val counts = countByFilter(games)

        assertEquals(3, counts[BacklogFilter.TOUS])
        assertEquals(1, counts[BacklogFilter.TERMINE])
        assertEquals(1, counts[BacklogFilter.A_FAIRE])
        assertEquals(1, counts[BacklogFilter.EN_COURS])
        assertEquals(0, counts[BacklogFilter.ABANDONNE])
    }

    @Test
    fun `completedYear derive l'annee de completion depuis completedAt`() {
        assertEquals(2023, completedYear(hades.copy(completedAt = completedIn2023)))
        assertEquals(null, completedYear(hades))
    }

    @Test
    fun `availableCompletedYears ne considere que les jeux Termine, sans doublon, du plus recent au plus ancien`() {
        val celeste = Game(id = "4", title = "Celeste", platform = "PC", genre = "Plateforme", status = GameStatus.TERMINE, completedAt = completedIn2024)
        val hadesTermine2023 = hades.copy(completedAt = completedIn2023)
        val zeldaTermine2023 = zelda.copy(status = GameStatus.TERMINE, completedAt = completedIn2023)

        val years = availableCompletedYears(listOf(hadesTermine2023, zeldaTermine2023, celeste, eldenRing))

        assertEquals(listOf(2024, 2023), years)
    }

    @Test
    fun `filtre par annee de completion ne garde que les jeux Termine cette annee-la`() {
        val celeste = Game(id = "4", title = "Celeste", platform = "PC", genre = "Plateforme", status = GameStatus.TERMINE, completedAt = completedIn2024)
        val hadesTermine2023 = hades.copy(completedAt = completedIn2023)

        val result = filterGames(listOf(hadesTermine2023, celeste, eldenRing), BacklogFilter.TERMINE, selectedYear = 2024)

        assertEquals(listOf(celeste), result)
    }

    @Test
    fun `le filtre par annee ne s'applique pas en dehors du filtre Termine ou Abandonne`() {
        val eldenRingTermine2023 = eldenRing.copy(status = GameStatus.TERMINE, completedAt = completedIn2023)

        // TOUS ignore l'année sélectionnée : un jeu "à faire" sans completedAt reste visible.
        val result = filterGames(listOf(eldenRingTermine2023, zelda), BacklogFilter.TOUS, selectedYear = 2024)

        assertEquals(listOf(eldenRingTermine2023, zelda), result)
    }

    @Test
    fun `abandonedYear derive l'annee d'abandon depuis abandonedAt`() {
        assertEquals(2023, abandonedYear(eldenRing.copy(abandonedAt = completedIn2023)))
        assertEquals(null, abandonedYear(eldenRing))
    }

    @Test
    fun `availableAbandonedYears ne considere que les jeux Abandonne, sans doublon, du plus recent au plus ancien`() {
        val celeste = Game(id = "4", title = "Celeste", platform = "PC", genre = "Plateforme", status = GameStatus.ABANDONNE, abandonedAt = completedIn2024)
        val eldenRingAbandonne2023 = eldenRing.copy(status = GameStatus.ABANDONNE, abandonedAt = completedIn2023)
        val zeldaAbandonne2023 = zelda.copy(status = GameStatus.ABANDONNE, abandonedAt = completedIn2023)

        val years = availableAbandonedYears(listOf(eldenRingAbandonne2023, zeldaAbandonne2023, celeste, hades))

        assertEquals(listOf(2024, 2023), years)
    }

    @Test
    fun `filtre par annee d'abandon ne garde que les jeux Abandonne cette annee-la`() {
        val celeste = Game(id = "4", title = "Celeste", platform = "PC", genre = "Plateforme", status = GameStatus.ABANDONNE, abandonedAt = completedIn2024)
        val eldenRingAbandonne2023 = eldenRing.copy(status = GameStatus.ABANDONNE, abandonedAt = completedIn2023)

        val result = filterGames(listOf(eldenRingAbandonne2023, celeste, hades), BacklogFilter.ABANDONNE, selectedYear = 2024)

        assertEquals(listOf(celeste), result)
    }

    @Test
    fun `availableYearsFor renvoie les annees selon l'onglet actif`() {
        val hadesTermine2023 = hades.copy(completedAt = completedIn2023)
        val eldenRingAbandonne2024 = eldenRing.copy(status = GameStatus.ABANDONNE, abandonedAt = completedIn2024)
        val all = listOf(hadesTermine2023, eldenRingAbandonne2024, zelda)

        assertEquals(listOf(2023), availableYearsFor(BacklogFilter.TERMINE, all))
        assertEquals(listOf(2024), availableYearsFor(BacklogFilter.ABANDONNE, all))
        assertEquals(emptyList<Int>(), availableYearsFor(BacklogFilter.TOUS, all))
    }
}
