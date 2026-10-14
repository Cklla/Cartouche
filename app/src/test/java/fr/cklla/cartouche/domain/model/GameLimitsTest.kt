package fr.cklla.cartouche.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLimitsTest {

    private val game = Game(id = "g", title = "Hades", platform = "PC/Switch", genre = "Roguelike", status = GameStatus.A_FAIRE)

    @Test
    fun `une liste de plateformes qui tient est jointe telle quelle`() {
        assertEquals("PC/PS5/Switch", joinPlatformsWithinLimit(listOf("PC", "PS5", "Switch")))
        assertEquals("", joinPlatformsWithinLimit(emptyList()))
    }

    @Test
    fun `les plateformes qui ne tiennent plus sont ecartees en entier`() {
        val names = List(30) { "Plateforme-$it" } // 13 ou 14 caractères chacune

        val joined = joinPlatformsWithinLimit(names)

        assertTrue(joined.length <= MAX_PLATFORM_LENGTH)
        // Chaque morceau est un nom complet, jamais coupé au milieu.
        assertTrue(joined.split("/").all { it in names })
        // Et c'est bien le début de la liste qui est gardé, dans l'ordre.
        assertEquals(names.take(joined.split("/").size), joined.split("/"))
    }

    @Test
    fun `une liste pile a la borne est gardee entiere`() {
        val name = "x".repeat(MAX_PLATFORM_LENGTH)

        assertEquals(name, joinPlatformsWithinLimit(listOf(name)))
        assertEquals("", joinPlatformsWithinLimit(listOf(name + "x")))
    }

    @Test
    fun `une annee hors des bornes du serveur devient null`() {
        assertNull(releaseYearOrNull(1949))
        assertNull(releaseYearOrNull(3000))
        assertNull(releaseYearOrNull(null))
        assertEquals(1950, releaseYearOrNull(1950))
        assertEquals(2999, releaseYearOrNull(2999))
    }

    @Test
    fun `un jeu deja dans les bornes ne change pas`() {
        val full = game.copy(
            title = "t".repeat(MAX_TITLE_LENGTH),
            genre = "g".repeat(MAX_GENRE_LENGTH),
            notes = "n".repeat(MAX_NOTES_LENGTH),
            releaseYear = 1950,
            userPlaytimeHours = MAX_HOURS,
            estimatedPlaytimeNormallyHours = MAX_HOURS,
            coverUrl = "https://" + "c".repeat(MAX_COVER_URL_LENGTH - 8),
            playedPlatforms = (1..MAX_PLAYED_PLATFORMS).map { "P$it" }.toSet(),
        )

        assertEquals(full, full.withinServerLimits())
    }

    @Test
    fun `chaque texte est ramene a sa borne`() {
        val result = game.copy(
            title = "t".repeat(MAX_TITLE_LENGTH + 1),
            genre = "g".repeat(MAX_GENRE_LENGTH + 1),
            notes = "n".repeat(MAX_NOTES_LENGTH + 1),
        ).withinServerLimits()

        assertEquals(MAX_TITLE_LENGTH, result.title.length)
        assertEquals(MAX_GENRE_LENGTH, result.genre.length)
        assertEquals(MAX_NOTES_LENGTH, result.notes.length)
    }

    @Test
    fun `une plateforme trop longue est ramenee a des noms entiers`() {
        val platform = List(40) { "Plateforme-$it" }.joinToString("/")

        val result = game.copy(platform = platform).withinServerLimits()

        assertTrue(result.platform.length <= MAX_PLATFORM_LENGTH)
        assertTrue(platform.startsWith(result.platform))
        assertTrue(platform[result.platform.length] == '/')
    }

    @Test
    fun `une annee ou une duree hors borne devient null, une URL trop longue est ecartee`() {
        val result = game.copy(
            releaseYear = 1900,
            estimatedPlaytimeHastilyHours = MAX_HOURS + 1,
            estimatedPlaytimeNormallyHours = -1,
            estimatedPlaytimeCompletelyHours = 10,
            coverUrl = "https://" + "c".repeat(MAX_COVER_URL_LENGTH),
        ).withinServerLimits()

        assertNull(result.releaseYear)
        assertNull(result.estimatedPlaytimeHastilyHours)
        assertNull(result.estimatedPlaytimeNormallyHours)
        assertEquals(10, result.estimatedPlaytimeCompletelyHours)
        assertNull(result.coverUrl)
    }

    @Test
    fun `le temps de jeu saisi est borne`() {
        assertEquals(MAX_HOURS, game.copy(userPlaytimeHours = MAX_HOURS + 5).withinServerLimits().userPlaytimeHours)
        assertEquals(0, game.copy(userPlaytimeHours = -3).withinServerLimits().userPlaytimeHours)
    }

    @Test
    fun `les plateformes jouees sont limitees a 20, en gardant les premieres par ordre alphabetique`() {
        val many = (1..25).map { "P%02d".format(it) }.toSet()

        val result = game.copy(playedPlatforms = many).withinServerLimits().playedPlatforms

        assertEquals(MAX_PLAYED_PLATFORMS, result.size)
        assertEquals(many.sorted().take(MAX_PLAYED_PLATFORMS).toSet(), result)
    }
}
