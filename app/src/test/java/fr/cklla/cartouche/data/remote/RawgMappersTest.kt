package fr.cklla.cartouche.data.remote

import fr.cklla.cartouche.data.remote.dto.RawgGameDto
import fr.cklla.cartouche.data.remote.dto.RawgGenreDto
import fr.cklla.cartouche.data.remote.dto.RawgPlatformDto
import fr.cklla.cartouche.data.remote.dto.RawgPlatformWrapperDto
import fr.cklla.cartouche.data.remote.dto.RawgStoreLinkDto
import org.junit.Assert.assertEquals
import fr.cklla.cartouche.domain.model.MAX_GENRE_LENGTH
import fr.cklla.cartouche.domain.model.MAX_PLATFORM_LENGTH
import fr.cklla.cartouche.domain.model.MAX_TITLE_LENGTH
import fr.cklla.cartouche.domain.model.toGame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `formatPlatforms reste dans la borne du serveur sans couper un nom de plateforme`() {
        val names = List(40) { "Plateforme-$it" }
        val platforms = names.mapIndexed { i, n -> RawgPlatformWrapperDto(RawgPlatformDto(id = i.toLong(), name = n)) }

        val joined = formatPlatforms(platforms)

        assertTrue(joined.length <= MAX_PLATFORM_LENGTH)
        assertTrue(joined.split("/").all { it in names })
    }

    @Test
    fun `un titre ou un genre trop longs sont coupes a la borne du serveur`() {
        val dto = RawgGameDto(
            id = 1,
            name = "t".repeat(MAX_TITLE_LENGTH + 50),
            genres = listOf(RawgGenreDto(id = 1, name = "g".repeat(MAX_GENRE_LENGTH + 50))),
        )

        val result = dto.toDomain()

        assertEquals(MAX_TITLE_LENGTH, result.title.length)
        assertEquals(MAX_GENRE_LENGTH, result.genre.length)
    }

    @Test
    fun `une annee de sortie anterieure a 1950 n'est pas retenue pour le jeu ajoute`() {
        val result = RawgGameDto(id = 1, name = "Spacewar!", released = "1962-04-01").toDomain()
        assertEquals("1962", result.year)

        val old = result.copy(year = "1949")
        assertNull(old.toGame().releaseYear)
        assertEquals(1962, result.toGame().releaseYear)
        assertNull(result.copy(year = "").toGame().releaseYear)
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

    @Test
    fun `extractSteamAppId trouve l'App ID dans le lien boutique Steam`() {
        val storeLinks = listOf(
            RawgStoreLinkDto(id = 1, storeId = 2, url = "https://www.playstation.com/fr-fr/games/hades/"),
            RawgStoreLinkDto(id = 2, storeId = 1, url = "https://store.steampowered.com/app/1145360/Hades/"),
        )
        assertEquals(1145360L, extractSteamAppId(storeLinks))
    }

    @Test
    fun `extractSteamAppId renvoie null sans lien boutique Steam`() {
        val storeLinks = listOf(
            RawgStoreLinkDto(id = 1, storeId = 2, url = "https://www.playstation.com/fr-fr/games/hades/"),
        )
        assertNull(extractSteamAppId(storeLinks))
    }

    @Test
    fun `extractSteamAppId renvoie null si le lien Steam est mal forme`() {
        val storeLinks = listOf(RawgStoreLinkDto(id = 1, storeId = 1, url = null))
        assertNull(extractSteamAppId(storeLinks))
    }
}
