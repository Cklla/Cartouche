package fr.cklla.cartouche.data.remote

import fr.cklla.cartouche.data.remote.dto.RawgGameDto
import fr.cklla.cartouche.data.remote.dto.RawgGenreDto
import fr.cklla.cartouche.data.remote.dto.RawgPlatformDto
import fr.cklla.cartouche.data.remote.dto.RawgPlatformWrapperDto
import org.junit.Assert.assertEquals
import org.junit.Test

class RawgMappersTest {

    @Test
    fun `extractYear prend les 4 premiers caracteres d'une date connue`() {
        assertEquals("2023", extractYear("2023-05-12"))
    }

    @Test
    fun `extractYear renvoie une chaine vide quand la date est inconnue`() {
        assertEquals("", extractYear(null))
    }

    @Test
    fun `formatPlatforms joint les noms de plateformes avec un slash`() {
        val platforms = listOf(
            RawgPlatformWrapperDto(RawgPlatformDto(id = 1, name = "PC")),
            RawgPlatformWrapperDto(RawgPlatformDto(id = 2, name = "PS5")),
        )
        assertEquals("PC/PS5", formatPlatforms(platforms))
    }

    @Test
    fun `formatPlatforms renvoie une chaine vide sans plateforme connue`() {
        assertEquals("", formatPlatforms(null))
        assertEquals("", formatPlatforms(emptyList()))
    }

    @Test
    fun `firstGenre prend le premier genre renvoye par RAWG`() {
        val genres = listOf(RawgGenreDto(id = 1, name = "Aventure"), RawgGenreDto(id = 2, name = "RPG"))
        assertEquals("Aventure", firstGenre(genres))
    }

    @Test
    fun `firstGenre renvoie une chaine vide sans genre connu`() {
        assertEquals("", firstGenre(null))
        assertEquals("", firstGenre(emptyList()))
    }

    @Test
    fun `toDomain convertit un jeu RAWG complet en resultat de recherche`() {
        val dto = RawgGameDto(
            id = 42,
            name = "Hades",
            released = "2020-09-17",
            backgroundImage = "https://example.com/hades.jpg",
            platforms = listOf(RawgPlatformWrapperDto(RawgPlatformDto(id = 1, name = "PC"))),
            genres = listOf(RawgGenreDto(id = 1, name = "Roguelike")),
        )

        val result = dto.toDomain()

        assertEquals(42L, result.rawgId)
        assertEquals("Hades", result.title)
        assertEquals("PC", result.platform)
        assertEquals("Roguelike", result.genre)
        assertEquals("2020", result.year)
        assertEquals("https://example.com/hades.jpg", result.coverUrl)
    }

    @Test
    fun `toDomain gere un jeu RAWG sans plateforme, genre ni date`() {
        val dto = RawgGameDto(id = 1, name = "Mystère")

        val result = dto.toDomain()

        assertEquals("", result.platform)
        assertEquals("", result.genre)
        assertEquals("", result.year)
        assertEquals(null, result.coverUrl)
    }
}
