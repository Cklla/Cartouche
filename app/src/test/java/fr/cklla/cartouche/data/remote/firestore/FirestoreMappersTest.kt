package fr.cklla.cartouche.data.remote.firestore

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirestoreMappersTest {

    @Test
    fun `toFirestoreMap puis mapToGame conserve toutes les donnees`() {
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
        )

        val roundTripped = mapToGame(game.id, game.toFirestoreMap())

        assertEquals(game, roundTripped)
    }

    @Test
    fun `toFirestoreMap n'inclut pas l'id`() {
        val game = Game(id = "42", title = "t", platform = "p", genre = "g", status = GameStatus.A_FAIRE)

        assertEquals(false, game.toFirestoreMap().containsKey("id"))
    }

    @Test
    fun `mapToGame lit des nombres remontes en Long, comme le fait Firestore`() {
        // Firestore n'a pas de type Int natif : tout nombre entier remonte en Long côté SDK réel.
        val data = mapOf(
            "title" to "t",
            "platform" to "p",
            "genre" to "g",
            "status" to GameStatus.EN_COURS.name,
            "rawgId" to 123L,
            "releaseYear" to 2024L,
            "userPlaytimeHours" to 10L,
            "estimatedPlaytimeHastilyHours" to 5L,
            "estimatedPlaytimeNormallyHours" to 8L,
            "estimatedPlaytimeCompletelyHours" to 15L,
            "rating" to 4L,
            "notes" to "",
            "completedAt" to 1_700_000_000_000L,
        )

        val game = mapToGame("1", data)

        assertEquals(123L, game?.rawgId)
        assertEquals(2024, game?.releaseYear)
        assertEquals(10, game?.userPlaytimeHours)
        assertEquals(5, game?.estimatedPlaytimeHastilyHours)
        assertEquals(4, game?.rating)
        assertEquals(1_700_000_000_000L, game?.completedAt)
    }

    @Test
    fun `mapToGame renvoie null si le statut est absent`() {
        val data = mapOf("title" to "t", "platform" to "p", "genre" to "g")

        assertNull(mapToGame("1", data))
    }

    @Test
    fun `mapToGame renvoie null si le statut est inconnu`() {
        val data = mapOf(
            "title" to "t",
            "platform" to "p",
            "genre" to "g",
            "status" to "STATUT_INEXISTANT",
        )

        assertNull(mapToGame("1", data))
    }

    @Test
    fun `mapToGame renvoie null si un champ obligatoire est manquant`() {
        val data = mapOf("title" to "t", "status" to GameStatus.A_FAIRE.name)

        assertNull(mapToGame("1", data))
    }

    @Test
    fun `mapToGame retombe sur des valeurs par defaut pour les champs optionnels absents`() {
        val data = mapOf(
            "title" to "t",
            "platform" to "p",
            "genre" to "g",
            "status" to GameStatus.A_FAIRE.name,
        )

        val game = mapToGame("1", data)

        assertEquals(0, game?.userPlaytimeHours)
        assertEquals("", game?.notes)
        assertNull(game?.rawgId)
        assertNull(game?.releaseYear)
        assertNull(game?.estimatedPlaytimeHastilyHours)
        assertNull(game?.rating)
        assertNull(game?.coverUrl)
        assertNull(game?.completedAt)
    }
}
