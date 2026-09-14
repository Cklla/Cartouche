package fr.cklla.cartouche.data.repository

import fr.cklla.cartouche.data.local.entity.GameEntity
import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class GameMappersTest {

    @Test
    fun `toEntity puis toDomain conserve toutes les donnees`() {
        val game = Game(
            id = 42L,
            title = "Hollow Knight",
            platform = "PC",
            genre = "Metroidvania",
            status = GameStatus.TERMINE,
            userPlaytimeHours = 35,
            estimatedPlaytimeHastilyHours = 28,
            estimatedPlaytimeNormallyHours = 40,
            estimatedPlaytimeCompletelyHours = 65,
            rating = 5,
            notes = "Excellent",
            coverUrl = "https://example.com/cover.jpg",
        )

        val roundTripped = game.toEntity().toDomain()

        assertEquals(game, roundTripped)
    }

    @Test
    fun `toDomain convertit correctement le statut stocke en texte`() {
        val entity = GameEntity(
            id = 1L,
            title = "t",
            platform = "p",
            genre = "g",
            status = GameStatus.ABANDONNE.name,
            userPlaytimeHours = 0,
            estimatedPlaytimeHastilyHours = null,
            estimatedPlaytimeNormallyHours = null,
            estimatedPlaytimeCompletelyHours = null,
            rating = null,
            notes = "",
            coverUrl = null,
        )

        assertEquals(GameStatus.ABANDONNE, entity.toDomain().status)
    }
}
