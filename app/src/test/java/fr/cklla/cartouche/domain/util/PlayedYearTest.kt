package fr.cklla.cartouche.domain.util

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import fr.cklla.cartouche.ui.abandonedYear
import fr.cklla.cartouche.ui.completedYear
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayedYearTest {

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 12, zone: ZoneId = ZoneOffset.UTC): Long =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    // « Maintenant » : 2026-10-04 à midi UTC.
    private val now = millis(2026, 10, 4)
    private val utc: ZoneId = ZoneOffset.UTC

    private fun yearFor(
        year: Int,
        current: Long? = null,
        releaseYear: Int? = null,
        nowMillis: Long = now,
        zone: ZoneId = utc,
    ) = timestampForYear(year, current, releaseYear, nowMillis, zone)

    @Test
    fun `une annee passee donne le 1er juillet a midi`() {
        assertEquals(millis(2024, 7, 1), yearFor(2024, releaseYear = 2021))
    }

    @Test
    fun `une annee passee suit le fuseau fourni`() {
        val paris = ZoneId.of("Europe/Paris")

        assertEquals(millis(2025, 7, 1, zone = paris), yearFor(2025, zone = paris))
    }

    @Test
    fun `l annee en cours ne change rien si la date est deja dans l annee`() {
        val existing = millis(2026, 2, 14)

        assertEquals(existing, yearFor(2026, current = existing, releaseYear = 2020))
    }

    @Test
    fun `l annee en cours depuis une annee passee donne l instant present`() {
        assertEquals(now, yearFor(2026, current = millis(2024, 7, 1), releaseYear = 2020))
    }

    @Test
    fun `l annee en cours sans date connue donne l instant present`() {
        assertEquals(now, yearFor(2026, current = null))
    }

    @Test
    fun `reprendre l annee deja enregistree conserve la date exacte`() {
        val existing = millis(2024, 3, 9, hour = 21)

        assertEquals(existing, yearFor(2024, current = existing, releaseYear = 2020))
    }

    @Test
    fun `une annee future est refusee`() {
        assertNull(yearFor(2027, releaseYear = 2020))
    }

    @Test
    fun `une annee avant la sortie est refusee`() {
        assertNull(yearFor(2019, releaseYear = 2020))
        assertEquals(millis(2020, 7, 1), yearFor(2020, releaseYear = 2020))
    }

    @Test
    fun `sans annee de sortie la borne basse est 1970`() {
        assertNull(yearFor(1969))
        assertEquals(millis(1970, 7, 1), yearFor(1970))
    }

    @Test
    fun `une sortie anterieure a 1970 ne descend jamais sous 1970`() {
        // Un horodatage de 1962 serait négatif, donc refusé par les règles Firestore.
        assertNull(yearFor(1962, releaseYear = 1962))
        assertEquals(1970, selectablePlayedYears(releaseYear = 1962, nowMillis = now, zone = utc).last())
    }

    @Test
    fun `un jeu pas encore sorti reste rattache a l annee en cours`() {
        assertEquals(listOf(2026), selectablePlayedYears(releaseYear = 2027, nowMillis = now, zone = utc))
        assertEquals(now, yearFor(2026, current = null, releaseYear = 2027))
        assertNull(yearFor(2027, releaseYear = 2027))
    }

    @Test
    fun `les annees proposees vont de l annee en cours a l annee de sortie`() {
        assertEquals(listOf(2026, 2025, 2024, 2023), selectablePlayedYears(releaseYear = 2023, nowMillis = now, zone = utc))
    }

    @Test
    fun `les annees proposees descendent jusqu a 1970 sans annee de sortie`() {
        val years = selectablePlayedYears(releaseYear = null, nowMillis = now, zone = utc)

        assertEquals(2026, years.first())
        assertEquals(1970, years.last())
        assertEquals(2026 - 1970 + 1, years.size)
    }

    @Test
    fun `l annee de l instant present suit le fuseau fourni`() {
        // 2026-12-31 23:30 UTC : déjà 2027 à Paris.
        val lateNow = millis(2026, 12, 31, hour = 23) + 30 * 60_000
        val paris = ZoneId.of("Europe/Paris")

        assertEquals(2027, selectablePlayedYears(releaseYear = 2025, nowMillis = lateNow, zone = paris).first())
        assertEquals(2026, selectablePlayedYears(releaseYear = 2025, nowMillis = lateNow, zone = utc).first())
    }

    @Test
    fun `completedYear et abandonedYear relisent l annee choisie sous les fuseaux extremes`() {
        val original = TimeZone.getDefault()
        try {
            listOf(ZoneOffset.ofHours(-12), ZoneOffset.ofHours(14), ZoneOffset.UTC).forEach { offset ->
                TimeZone.setDefault(TimeZone.getTimeZone(offset))
                val zone = ZoneId.systemDefault()

                listOf(1970, 1999, 2024, 2025).forEach { year ->
                    val timestamp = yearFor(year, zone = zone)!!
                    val jeuTermine = Game(title = "A", platform = "PC", genre = "RPG", status = GameStatus.TERMINE, completedAt = timestamp)
                    val jeuAbandonne = Game(title = "B", platform = "PC", genre = "RPG", status = GameStatus.ABANDONNE, abandonedAt = timestamp)

                    assertEquals("completedYear $year sous $offset", year, completedYear(jeuTermine))
                    assertEquals("abandonedYear $year sous $offset", year, abandonedYear(jeuAbandonne))
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `l horodatage du 1er juillet reste dans la bonne annee dans tous les fuseaux`() {
        val timestamp = yearFor(2024)!!

        (-12..14).forEach { hours ->
            val year = Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.ofHours(hours)).year
            assertEquals("UTC$hours", 2024, year)
        }
    }

    @Test
    fun `l horodatage de 1970 reste positif dans tous les fuseaux`() {
        // Les règles Firestore refusent un completedAt / abandonedAt négatif.
        (-12..14).forEach { hours ->
            val timestamp = yearFor(1970, zone = ZoneOffset.ofHours(hours))!!
            assertTrue("UTC$hours", timestamp > 0)
        }
    }
}
