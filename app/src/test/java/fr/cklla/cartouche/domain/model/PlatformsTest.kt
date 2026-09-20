package fr.cklla.cartouche.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformsTest {

    @Test
    fun `parsePlatforms decoupe une chaine multi-plateformes jointe par des slash`() {
        assertEquals(listOf("PC", "PS5", "Switch"), parsePlatforms("PC/PS5/Switch"))
    }

    @Test
    fun `parsePlatforms renvoie une liste d'un seul element sans slash`() {
        assertEquals(listOf("PC"), parsePlatforms("PC"))
    }

    @Test
    fun `parsePlatforms ignore les espaces autour de chaque plateforme`() {
        assertEquals(listOf("PC", "PS5"), parsePlatforms("PC / PS5"))
    }

    @Test
    fun `parsePlatforms renvoie une liste vide pour une chaine vide`() {
        assertEquals(emptyList<String>(), parsePlatforms(""))
    }

    @Test
    fun `parsePlatforms deduplique les plateformes identiques`() {
        assertEquals(listOf("PC"), parsePlatforms("PC/PC"))
    }

    @Test
    fun `effectivePlayedPlatforms renvoie l'unique plateforme meme si playedPlatforms est vide`() {
        val game = Game(title = "Xenoblade Chronicles 2", platform = "Switch", genre = "RPG", status = GameStatus.EN_COURS)

        assertEquals(setOf("Switch"), effectivePlayedPlatforms(game))
    }

    @Test
    fun `effectivePlayedPlatforms ignore playedPlatforms quand une seule plateforme existe`() {
        // Cas impossible via l'UI (pas de case a cocher sur un jeu mono-plateforme) mais qui peut
        // survenir en donnees : la plateforme unique prime toujours.
        val game = Game(
            title = "Xenoblade Chronicles 2",
            platform = "Switch",
            genre = "RPG",
            status = GameStatus.EN_COURS,
            playedPlatforms = setOf("PC"),
        )

        assertEquals(setOf("Switch"), effectivePlayedPlatforms(game))
    }

    @Test
    fun `effectivePlayedPlatforms s'en tient a playedPlatforms pour un jeu multi-plateformes`() {
        val game = Game(
            title = "Trails in the Sky First Chapter",
            platform = "PC/PS5/Switch",
            genre = "RPG",
            status = GameStatus.EN_COURS,
            playedPlatforms = setOf("Switch"),
        )

        assertEquals(setOf("Switch"), effectivePlayedPlatforms(game))
    }

    @Test
    fun `effectivePlayedPlatforms renvoie une liste vide pour un jeu multi-plateformes jamais coche`() {
        val game = Game(title = "Trails in the Sky First Chapter", platform = "PC/PS5/Switch", genre = "RPG", status = GameStatus.EN_COURS)

        assertEquals(emptySet<String>(), effectivePlayedPlatforms(game))
    }
}
