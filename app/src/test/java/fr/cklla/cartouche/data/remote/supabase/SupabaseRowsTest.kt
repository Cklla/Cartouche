package fr.cklla.cartouche.data.remote.supabase

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseRowsTest {

    // Même configuration que le client par défaut du SDK.
    private val json = Json { ignoreUnknownKeys = true }

    private val game = Game(
        id = "5d1a7a3e-0000-4000-8000-000000000001",
        title = "Hollow Knight",
        platform = "PC/Switch",
        genre = "Metroidvania",
        status = GameStatus.TERMINE,
        rawgId = 123L,
        releaseYear = 2017,
        userPlaytimeHours = 35,
        estimatedPlaytimeHastilyHours = 28,
        estimatedPlaytimeNormallyHours = 40,
        estimatedPlaytimeCompletelyHours = 65,
        rating = 5,
        notes = "Excellent",
        coverUrl = "https://exemple.fr/cover.jpg",
        completedAt = 1_700_000_000_000L,
        abandonedAt = null,
        playedPlatforms = setOf("Switch", "PC"),
        igdbLookupAttempted = true,
    )

    private fun encode(row: GameRow) = json.parseToJsonElement(json.encodeToString(GameRow.serializer(), row)).jsonObject

    @Test
    fun `un jeu fait l'aller-retour par son JSON`() {
        val row = json.decodeFromString<GameRow>(json.encodeToString(GameRow.serializer(), game.toRow()))

        assertEquals(game, row.toGameOrNull())
    }

    @Test
    fun `les colonnes portent les noms de la base`() {
        val row = encode(game.toRow())

        assertEquals("Hollow Knight", row.getValue("title").jsonPrimitive.content)
        assertEquals("TERMINE", row.getValue("status").jsonPrimitive.content)
        assertEquals("123", row.getValue("rawg_id").jsonPrimitive.content)
        assertEquals(2017, row.getValue("release_year").jsonPrimitive.int)
        assertEquals(35, row.getValue("user_playtime_hours").jsonPrimitive.int)
        assertEquals(28, row.getValue("estimated_playtime_hastily_hours").jsonPrimitive.int)
        assertEquals(40, row.getValue("estimated_playtime_normally_hours").jsonPrimitive.int)
        assertEquals(65, row.getValue("estimated_playtime_completely_hours").jsonPrimitive.int)
        assertEquals("https://exemple.fr/cover.jpg", row.getValue("cover_url").jsonPrimitive.content)
        assertEquals("1700000000000", row.getValue("completed_at").jsonPrimitive.content)
        assertEquals("true", row.getValue("igdb_lookup_attempted").jsonPrimitive.content)
        assertFalse("user_id est rempli par le serveur", row.containsKey("user_id"))
    }

    @Test
    fun `les plateformes jouees partent en tableau trie`() {
        val platforms: JsonArray = encode(game.toRow()).getValue("played_platforms").jsonArray

        assertEquals(listOf("PC", "Switch"), platforms.map { it.jsonPrimitive.content })
    }

    @Test
    fun `une valeur absente est envoyee comme null pour effacer la colonne`() {
        val cleared = game.copy(
            rating = null,
            rawgId = null,
            releaseYear = null,
            coverUrl = null,
            completedAt = null,
            abandonedAt = null,
            estimatedPlaytimeHastilyHours = null,
            estimatedPlaytimeNormallyHours = null,
            estimatedPlaytimeCompletelyHours = null,
        )

        val row = encode(cleared.toRow())

        // Sans le null explicite, un upsert laisserait l'ancienne valeur en base.
        listOf(
            "rating", "rawg_id", "release_year", "cover_url", "completed_at", "abandoned_at",
            "estimated_playtime_hastily_hours", "estimated_playtime_normally_hours", "estimated_playtime_completely_hours",
        ).forEach { column -> assertEquals("colonne $column", JsonNull, row[column]) }
    }

    @Test
    fun `les colonnes not null ne partent jamais en null`() {
        // Un backlog minimal : temps de jeu 0, notes vides, aucune plateforme jouée. Ces trois colonnes
        // sont `not null` côté base (refus 23502, donc écriture abandonnée puis ligne locale supprimée).
        val minimal = Game(id = "g", title = "t", platform = "p", genre = "g", status = GameStatus.A_FAIRE)

        val row = encode(minimal.toRow())

        assertEquals(JsonPrimitive(0), row.getValue("user_playtime_hours"))
        assertEquals(JsonPrimitive(""), row.getValue("notes"))
        assertEquals(JsonArray(emptyList()), row.getValue("played_platforms"))
        assertEquals(JsonPrimitive(false), row.getValue("igdb_lookup_attempted"))
    }

    @Test
    fun `une ligne serveur avec des colonnes en plus est lue`() {
        val raw = """{"id":"x","user_id":"u","title":"Hades","platform":"Switch","genre":"Roguelike","status":"A_FAIRE",
            "rawg_id":null,"release_year":null,"user_playtime_hours":0,"estimated_playtime_hastily_hours":null,
            "estimated_playtime_normally_hours":null,"estimated_playtime_completely_hours":null,"rating":null,
            "notes":"","cover_url":null,"completed_at":null,"abandoned_at":null,"played_platforms":[],
            "igdb_lookup_attempted":false,"created_at":"2026-10-10T12:00:00Z"}"""

        val result = json.decodeFromString<GameRow>(raw).toGameOrNull()

        assertEquals(Game(id = "x", title = "Hades", platform = "Switch", genre = "Roguelike", status = GameStatus.A_FAIRE), result)
    }

    @Test
    fun `une ligne au statut inconnu est ignoree`() {
        val row = game.toRow()

        assertNull(row.copy(status = "EN_PAUSE").toGameOrNull())
        assertNull(row.copy(status = "").toGameOrNull())
    }

    @Test
    fun `une jaquette qui n'est pas en https est ecartee`() {
        assertNull(game.toRow().copy(coverUrl = "http://exemple.fr/a.jpg").toGameOrNull()?.coverUrl)
        assertNull(game.toRow().copy(coverUrl = "file:///etc/passwd").toGameOrNull()?.coverUrl)
    }

    // --- pagination ---

    @Test
    fun `une lecture plus grande qu'une page est lue en entier`() = runTest {
        val source = (1..2500).toList()
        val requested = mutableListOf<Pair<Long, Long>>()

        val all = fetchAllPages(1000) { from, to ->
            requested += from to to
            source.subList(from.toInt(), minOf(to.toInt() + 1, source.size))
        }

        assertEquals(source, all)
        assertEquals(listOf(0L to 999L, 1000L to 1999L, 2000L to 2999L), requested)
    }

    @Test
    fun `une lecture d'exactement une page demande la page suivante pour confirmer la fin`() = runTest {
        val source = (1..1000).toList()
        var calls = 0

        val all = fetchAllPages(1000) { from, to ->
            calls++
            source.subList(minOf(from.toInt(), source.size), minOf(to.toInt() + 1, source.size))
        }

        assertEquals(source, all)
        assertEquals(2, calls)
    }

    @Test
    fun `une lecture vide renvoie une liste vide`() = runTest {
        assertTrue(fetchAllPages(1000) { _, _ -> emptyList<Int>() }.isEmpty())
    }

    @Test
    fun `l'echec d'une page fait echouer toute la lecture au lieu de renvoyer un morceau`() = runTest {
        val failure = runCatching {
            fetchAllPages(2) { from, _ ->
                if (from >= 2) error("coupure") else listOf(1, 2)
            }
        }.exceptionOrNull()

        assertEquals("coupure", failure?.message)
    }

    // --- classification des erreurs ---

    @Test
    fun `les refus de fond sont definitifs, le reste se reessaie`() {
        // CHECK, NOT NULL, unicité, texte trop long, identifiant mal formé, caractère NUL.
        listOf("23514", "23502", "23505", "22001", "22P02", "22021").forEach { assertTrue(it, isPermanentFailure(it)) }
        // Colonne ou table inconnue du schéma déployé, droits (RLS ou GRANT), JWT expiré, code absent.
        listOf("PGRST204", "PGRST205", "42501", "42703", "PGRST301", "", "23").forEach {
            assertFalse(it, isPermanentFailure(it))
        }
        assertFalse(isPermanentFailure(null))
    }
}
