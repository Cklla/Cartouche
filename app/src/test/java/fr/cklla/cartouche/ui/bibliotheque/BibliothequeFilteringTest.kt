package fr.cklla.cartouche.ui.bibliotheque

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BibliothequeFilteringTest {

    private val hades = Game(id = "1", title = "Hades", platform = "PC", genre = "Roguelike", status = GameStatus.TERMINE)
    private val eldenRing = Game(id = "2", title = "Elden Ring", platform = "PS5", genre = "Action-RPG", status = GameStatus.A_FAIRE)
    private val zelda = Game(id = "3", title = "Zelda", platform = "Switch", genre = "Aventure", status = GameStatus.EN_COURS)
    private val games = listOf(hades, eldenRing, zelda)

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
}
