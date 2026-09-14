package fr.cklla.cartouche.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameSearchResultTest {

    @Test
    fun `toGame convertit un resultat de recherche en jeu du backlog a faire`() {
        val result = GameSearchResult(
            rawgId = 1L,
            title = "Hades",
            platform = "PC",
            genre = "Roguelike",
            year = "2020",
            coverUrl = "https://example.com/hades.jpg",
        )

        val game = result.toGame()

        assertEquals("Hades", game.title)
        assertEquals("PC", game.platform)
        assertEquals("Roguelike", game.genre)
        assertEquals("https://example.com/hades.jpg", game.coverUrl)
        assertEquals(GameStatus.A_FAIRE, game.status)
        assertEquals(0, game.userPlaytimeHours)
    }

    @Test
    fun `toGame laisse le temps de jeu estime a null, renseigne plus tard via IGDB`() {
        val result = GameSearchResult(
            rawgId = 1L,
            title = "Mystère",
            platform = "",
            genre = "",
            year = "",
            coverUrl = null,
        )

        assertNull(result.toGame().estimatedPlaytimeHours)
    }
}
