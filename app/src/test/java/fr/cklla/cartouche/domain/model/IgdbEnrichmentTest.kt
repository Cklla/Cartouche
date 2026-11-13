package fr.cklla.cartouche.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cas de la règle d'enrichissement IGDB. Les mêmes cas sont portés côté web : toute modification
 * ici doit l'être là-bas.
 */
class IgdbEnrichmentTest {

    private val igdbCover = "https://images.igdb.com/igdb/image/upload/t_cover_big_2x/coc8ap.jpg"
    private val rawgCover = "https://media.rawg.io/media/games/abc/abc.jpg"

    private val game = Game(
        id = "g1",
        title = "Xenoblade Chronicles",
        platform = "Nintendo Switch",
        genre = "JRPG",
        status = GameStatus.EN_COURS,
        rating = 4,
        notes = "Super",
        userPlaytimeHours = 12,
        completedAt = 1_700_000_000_000L,
        abandonedAt = 1_600_000_000_000L,
        coverUrl = rawgCover,
    )

    private val fullMatch = IgdbGameMatch(
        igdbId = 2364L,
        platform = "Nintendo Switch/Nintendo Switch 2/Wii",
        coverUrl = igdbCover,
        releaseYear = 2010,
        playtimeEstimate = IgdbPlaytimeEstimate(hastilyHours = 40, normallyHours = 70, completelyHours = 110),
    )

    // --- rapprochement non certain : ni id, ni jaquette, ni année ---

    @Test
    fun `un rapprochement sans id n'ecrit ni id, ni jaquette, ni annee mais garde plateformes et durees`() {
        val uncertain = IgdbGameMatch(
            igdbId = null,
            platform = "Nintendo Switch/Wii",
            coverUrl = null,
            releaseYear = null,
            playtimeEstimate = IgdbPlaytimeEstimate(hastilyHours = 40, normallyHours = 70, completelyHours = 110),
        )

        val result = applyIgdbEnrichment(game, uncertain)

        assertNull(result.igdbId)
        assertEquals(rawgCover, result.coverUrl)
        assertNull(result.releaseYear)
        assertEquals("Nintendo Switch/Wii", result.platform)
        assertEquals(70, result.estimatedPlaytimeNormallyHours)
        assertTrue(result.igdbLookupAttempted)
    }

    // --- igdb_id ---

    @Test
    fun `l'id IGDB est renseigne quand il est vide`() {
        assertEquals(2364L, applyIgdbEnrichment(game, fullMatch).igdbId)
    }

    @Test
    fun `un id IGDB deja connu n'est jamais change`() {
        assertEquals(111L, applyIgdbEnrichment(game.copy(igdbId = 111L), fullMatch).igdbId)
    }

    @Test
    fun `un id IGDB hors borne n'est pas enregistre`() {
        assertNull(applyIgdbEnrichment(game, fullMatch.copy(igdbId = 0L)).igdbId)
        assertNull(applyIgdbEnrichment(game, fullMatch.copy(igdbId = 10_000_000_000L)).igdbId)
    }

    // --- cover_url ---

    @Test
    fun `une jaquette RAWG est remplacee par celle d'IGDB`() {
        assertEquals(igdbCover, applyIgdbEnrichment(game, fullMatch).coverUrl)
    }

    @Test
    fun `une jaquette vide ou absente est remplacee par celle d'IGDB`() {
        assertEquals(igdbCover, applyIgdbEnrichment(game.copy(coverUrl = null), fullMatch).coverUrl)
        assertEquals(igdbCover, applyIgdbEnrichment(game.copy(coverUrl = ""), fullMatch).coverUrl)
    }

    @Test
    fun `une jaquette d'un autre hote est gardee`() {
        val other = "https://exemple.fr/cover.jpg"
        assertEquals(other, applyIgdbEnrichment(game.copy(coverUrl = other), fullMatch).coverUrl)
    }

    @Test
    fun `seul l'hote exact media rawg io compte comme jaquette RAWG`() {
        listOf(
            "https://media.rawg.io.evil.example/x.jpg",
            "https://evil.example/media.rawg.io/x.jpg",
            "https://sub.media.rawg.io/x.jpg",
            "https://media.rawg.io@evil.example/x.jpg",
        ).forEach { url ->
            assertEquals("url $url", url, applyIgdbEnrichment(game.copy(coverUrl = url), fullMatch).coverUrl)
        }
    }

    @Test
    fun `sans jaquette IGDB, la jaquette actuelle n'est pas ecrasee`() {
        assertEquals(rawgCover, applyIgdbEnrichment(game, fullMatch.copy(coverUrl = null)).coverUrl)
        assertNull(applyIgdbEnrichment(game.copy(coverUrl = null), fullMatch.copy(coverUrl = null)).coverUrl)
    }

    // --- platform ---

    @Test
    fun `la plateforme IGDB remplace celle du jeu quand aucune case n'est cochee`() {
        assertEquals("Nintendo Switch/Nintendo Switch 2/Wii", applyIgdbEnrichment(game, fullMatch).platform)
    }

    @Test
    fun `une case cochee est realignee sur la casse IGDB`() {
        val played = game.copy(platform = "switch", playedPlatforms = setOf("switch"))
        val result = applyIgdbEnrichment(played, fullMatch.copy(platform = "Switch/Wii"))

        assertEquals("Switch/Wii", result.platform)
        assertEquals(setOf("Switch"), result.playedPlatforms)
    }

    @Test
    fun `un jeu a plateforme unique est corrige quand IGDB est certain (Switch vers Switch 2)`() {
        val single = game.copy(platform = "Nintendo Switch", playedPlatforms = setOf("Nintendo Switch"))
        val result = applyIgdbEnrichment(single, fullMatch.copy(platform = "Nintendo Switch 2"))

        assertEquals("Nintendo Switch 2", result.platform)
        assertEquals(setOf("Nintendo Switch 2"), result.playedPlatforms)
    }

    @Test
    fun `un jeu a plateforme unique sans case cochee est corrige aussi`() {
        val single = game.copy(platform = "Nintendo Switch", playedPlatforms = emptySet())
        val result = applyIgdbEnrichment(single, fullMatch.copy(platform = "Nintendo Switch 2"))

        assertEquals("Nintendo Switch 2", result.platform)
        // Aucune case cochée : la plateforme jouée reste dérivée de `platform` (effectivePlayedPlatforms).
        assertEquals(emptySet<String>(), result.playedPlatforms)
    }

    @Test
    fun `un jeu a plateforme unique corrige vers plusieurs plateformes n'a plus de case cochee`() {
        val single = game.copy(platform = "PS3", playedPlatforms = setOf("PS3"))
        val result = applyIgdbEnrichment(single, fullMatch.copy(platform = "Nintendo Switch/Wii"))

        assertEquals("Nintendo Switch/Wii", result.platform)
        assertEquals(emptySet<String>(), result.playedPlatforms)
    }

    @Test
    fun `la plateforme jouee explicite d'un jeu mono-plateforme n'est pas ecrasee`() {
        // Plateforme enregistrée « PS3 », mais la case cochée est « Wii » : un vrai choix, à garder.
        val single = game.copy(platform = "PS3", playedPlatforms = setOf("Wii"))
        // La nouvelle liste ne contient pas « Wii » : le réalignement échoue et l'exception ne s'applique pas.
        val result = applyIgdbEnrichment(single, fullMatch.copy(platform = "Nintendo Switch 2"))

        assertEquals("PS3", result.platform)
        assertEquals(setOf("Wii"), result.playedPlatforms)
        // Le reste de la remise à niveau s'applique.
        assertEquals(2364L, result.igdbId)
    }

    @Test
    fun `une plateforme jouee a la casse differente de la plateforme unique est corrigee`() {
        val single = game.copy(platform = "nintendo switch", playedPlatforms = setOf("Nintendo Switch"))
        val result = applyIgdbEnrichment(single, fullMatch.copy(platform = "Nintendo Switch 2"))

        assertEquals("Nintendo Switch 2", result.platform)
        assertEquals(setOf("Nintendo Switch 2"), result.playedPlatforms)
    }

    @Test
    fun `une plateforme vide avec une plateforme jouee non vide garde la plateforme jouee`() {
        val single = game.copy(platform = "", playedPlatforms = setOf("PS3"))
        val result = applyIgdbEnrichment(single, fullMatch.copy(platform = "Nintendo Switch 2"))

        assertEquals("", result.platform)
        assertEquals(setOf("PS3"), result.playedPlatforms)
    }

    @Test
    fun `une plateforme unique a la casse differente vers une liste qui la contient garde la case cochee`() {
        // Cas du réalignement (2) : il passe avant l'exception du jeu à plateforme unique.
        val single = game.copy(platform = "pc", playedPlatforms = setOf("pc"))
        val result = applyIgdbEnrichment(single, fullMatch.copy(platform = "PC/PS5"))

        assertEquals("PC/PS5", result.platform)
        assertEquals(setOf("PC"), result.playedPlatforms)
    }

    @Test
    fun `un rapprochement non certain ne corrige pas la plateforme d'un jeu a plateforme unique`() {
        val single = game.copy(platform = "Nintendo Switch", playedPlatforms = setOf("Nintendo Switch"))
        val result = applyIgdbEnrichment(single, fullMatch.copy(igdbId = null, platform = "Nintendo Switch 2"))

        assertEquals("Nintendo Switch", result.platform)
        assertEquals(setOf("Nintendo Switch"), result.playedPlatforms)
    }

    @Test
    fun `la plateforme n'est pas remplacee si une case cochee d'un jeu multi-plateformes perdrait sa plateforme`() {
        val played = game.copy(platform = "Nintendo Switch/Wii", playedPlatforms = setOf("Nintendo Switch"))
        val result = applyIgdbEnrichment(played, fullMatch.copy(platform = "Nintendo Switch 2/Wii"))

        assertEquals("Nintendo Switch/Wii", result.platform)
        assertEquals(setOf("Nintendo Switch"), result.playedPlatforms)
        // Le reste de la remise à niveau, lui, s'applique.
        assertEquals(2364L, result.igdbId)
    }

    @Test
    fun `sans plateforme IGDB, la plateforme du jeu est gardee`() {
        assertEquals("Nintendo Switch", applyIgdbEnrichment(game, fullMatch.copy(platform = null)).platform)
        assertEquals("Nintendo Switch", applyIgdbEnrichment(game, fullMatch.copy(platform = "")).platform)
    }

    @Test
    fun `la nouvelle liste de plateformes est bornee avant remplacement`() {
        val long = (1..60).joinToString("/") { "Plateforme numero $it" }
        val result = applyIgdbEnrichment(game, fullMatch.copy(platform = long))

        assertTrue(result.platform.length <= MAX_PLATFORM_LENGTH)
        assertTrue(result.platform.startsWith("Plateforme numero 1/"))
    }

    // --- estimated_playtime_* ---

    @Test
    fun `seules les durees fournies par IGDB sont ecrites, les autres sont gardees`() {
        val existing = game.copy(
            estimatedPlaytimeHastilyHours = 5,
            estimatedPlaytimeNormallyHours = 6,
            estimatedPlaytimeCompletelyHours = 7,
        )
        val partial = fullMatch.copy(playtimeEstimate = IgdbPlaytimeEstimate(null, 70, null))

        val result = applyIgdbEnrichment(existing, partial)

        assertEquals(5, result.estimatedPlaytimeHastilyHours)
        assertEquals(70, result.estimatedPlaytimeNormallyHours)
        assertEquals(7, result.estimatedPlaytimeCompletelyHours)
    }

    @Test
    fun `sans estimation IGDB, les durees du jeu sont gardees`() {
        val existing = game.copy(estimatedPlaytimeNormallyHours = 6)

        assertEquals(6, applyIgdbEnrichment(existing, fullMatch.copy(playtimeEstimate = null)).estimatedPlaytimeNormallyHours)
    }

    // --- release_year ---

    @Test
    fun `l'annee est renseignee quand elle est vide et jamais changee ensuite`() {
        assertEquals(2010, applyIgdbEnrichment(game, fullMatch).releaseYear)
        assertEquals(1999, applyIgdbEnrichment(game.copy(releaseYear = 1999), fullMatch).releaseYear)
        assertNull(applyIgdbEnrichment(game, fullMatch.copy(releaseYear = null)).releaseYear)
        assertNull(applyIgdbEnrichment(game, fullMatch.copy(releaseYear = 1800)).releaseYear)
    }

    // --- igdb_lookup_attempted et champs intouchables ---

    @Test
    fun `la tentative est enregistree meme sans correspondance, sans rien changer d'autre`() {
        val result = applyIgdbEnrichment(game, null)

        assertTrue(result.igdbLookupAttempted)
        assertEquals(game.copy(igdbLookupAttempted = true), result)
    }

    @Test
    fun `titre, genre, statut, note, heures, notes, dates et plateformes jouees ne sont jamais modifies`() {
        val played = game.copy(playedPlatforms = setOf("Nintendo Switch"))
        val result = applyIgdbEnrichment(played, fullMatch)

        assertEquals(played.title, result.title)
        assertEquals(played.genre, result.genre)
        assertEquals(played.status, result.status)
        assertEquals(played.rating, result.rating)
        assertEquals(played.userPlaytimeHours, result.userPlaytimeHours)
        assertEquals(played.notes, result.notes)
        assertEquals(played.completedAt, result.completedAt)
        assertEquals(played.abandonedAt, result.abandonedAt)
        assertEquals(played.playedPlatforms, result.playedPlatforms)
        assertEquals(played.id, result.id)
        assertEquals(played.rawgId, result.rawgId)
    }

    @Test
    fun `appliquer deux fois la meme correspondance donne le meme jeu`() {
        val once = applyIgdbEnrichment(game, fullMatch)

        assertEquals(once, applyIgdbEnrichment(once, fullMatch))
    }
}
