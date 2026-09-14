package fr.cklla.cartouche.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameSearchResultTest {

    @Test
    fun `toGame fait remonter le temps de jeu estime vers le jeu du backlog`() {
        val result = GameSearchResult(
            rawgId = 1L,
            title = "Hades",
            platform = "PC",
            genre = "Roguelike",
            year = "2020",
            coverUrl = null,
            estimatedPlaytimeHours = 22,
        )

        val game = result.toGame()

        assertEquals(22, game.estimatedPlaytimeHours)
        assertEquals(0, game.userPlaytimeHours)
        assertEquals(GameStatus.A_FAIRE, game.status)
    }

    @Test
    fun `toGame garde le temps de jeu estime a null quand RAWG n'a pas la donnee`() {
        val result = GameSearchResult(
            rawgId = 1L,
            title = "Mystère",
            platform = "",
            genre = "",
            year = "",
            coverUrl = null,
            estimatedPlaytimeHours = null,
        )

        assertNull(result.toGame().estimatedPlaytimeHours)
    }
}
