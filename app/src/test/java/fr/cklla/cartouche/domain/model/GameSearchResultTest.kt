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
        assertEquals(1L, game.rawgId)
        assertEquals(2020, game.releaseYear)
    }

    @Test
    fun `toGame laisse releaseYear a null quand l'annee RAWG est inconnue`() {
        val result = GameSearchResult(
            rawgId = 1L,
            title = "Mystère",
            platform = "",
            genre = "",
            year = "",
            coverUrl = null,
        )

        assertNull(result.toGame().releaseYear)
    }

    @Test
    fun `toGame precoche automatiquement l'unique plateforme disponible`() {
        val result = GameSearchResult(
            rawgId = 1L,
            title = "Hades",
            platform = "PC",
            genre = "Roguelike",
            year = "2020",
            coverUrl = null,
        )

        assertEquals(setOf("PC"), result.toGame().playedPlatforms)
    }

    @Test
    fun `toGame laisse playedPlatforms vide quand plusieurs plateformes sont possibles`() {
        val result = GameSearchResult(
            rawgId = 1L,
            title = "Trails in the Sky First Chapter",
            platform = "PC/PS5/Switch",
            genre = "RPG",
            year = "2020",
            coverUrl = null,
        )

        assertEquals(emptySet<String>(), result.toGame().playedPlatforms)
    }

    @Test
    fun `toGame laisse les temps de jeu estimes a null, renseignes plus tard via IGDB`() {
        val result = GameSearchResult(
            rawgId = 1L,
            title = "Mystère",
            platform = "",
            genre = "",
            year = "",
            coverUrl = null,
        )

        val game = result.toGame()

        assertNull(game.estimatedPlaytimeHastilyHours)
        assertNull(game.estimatedPlaytimeNormallyHours)
        assertNull(game.estimatedPlaytimeCompletelyHours)
    }
}
