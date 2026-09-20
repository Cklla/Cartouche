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
            id = "42",
            title = "Hollow Knight",
            platform = "PC",
            genre = "Metroidvania",
            status = GameStatus.TERMINE,
            rawgId = 123L,
            releaseYear = 2019,
            userPlaytimeHours = 35,
            estimatedPlaytimeHastilyHours = 28,
            estimatedPlaytimeNormallyHours = 40,
            estimatedPlaytimeCompletelyHours = 65,
            rating = 5,
            notes = "Excellent",
            coverUrl = "https://example.com/cover.jpg",
            completedAt = 1_700_000_000_000L,
            abandonedAt = null,
            playedPlatforms = setOf("PC"),
        )

        val roundTripped = game.toEntity().toDomain()

        assertEquals(game, roundTripped)
    }

    @Test
    fun `abandonedAt est conserve au round-trip`() {
        val game = Game(
            id = "43",
            title = "Cyberpunk 2077",
            platform = "PC",
            genre = "Action-RPG",
            status = GameStatus.ABANDONNE,
            abandonedAt = 1_700_000_000_000L,
        )

        assertEquals(game, game.toEntity().toDomain())
    }

    @Test
    fun `toDomain convertit correctement le statut stocke en texte`() {
        val entity = GameEntity(
            id = "1",
            title = "t",
            platform = "p",
            genre = "g",
            status = GameStatus.ABANDONNE.name,
            rawgId = null,
            releaseYear = null,
            userPlaytimeHours = 0,
            estimatedPlaytimeHastilyHours = null,
            estimatedPlaytimeNormallyHours = null,
            estimatedPlaytimeCompletelyHours = null,
            rating = null,
            notes = "",
            coverUrl = null,
            completedAt = null,
            abandonedAt = null,
            playedPlatforms = "",
        )

        assertEquals(GameStatus.ABANDONNE, entity.toDomain().status)
    }

    @Test
    fun `playedPlatforms est converti entre Set et chaine jointe par des slash`() {
        val game = Game(
            id = "1",
            title = "Trails in the Sky First Chapter",
            platform = "PC/PS5/Switch",
            genre = "RPG",
            status = GameStatus.EN_COURS,
            playedPlatforms = setOf("Switch", "PC"),
        )

        val entity = game.toEntity()

        assertEquals("PC/Switch", entity.playedPlatforms)
        assertEquals(setOf("PC", "Switch"), entity.toDomain().playedPlatforms)
    }
}
