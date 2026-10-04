package fr.cklla.cartouche.ui.stats

import fr.cklla.cartouche.domain.model.Game
import fr.cklla.cartouche.domain.model.GameStatus
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapHighlightsTest {

    // `completedYear` lit le fuseau de l'appareil : les tests utilisent le même pour que l'année et
    // les jours calculés par les faits coïncident.
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun at(month: Int, day: Int, hour: Int = 20, zone: ZoneId = this.zone, year: Int = 2026): Long =
        ZonedDateTime.of(year, month, day, hour, 30, 0, 0, zone).toInstant().toEpochMilli()

    /** Le 1er juillet à midi pile, l'horodatage d'une année choisie à la main. */
    private fun approximate(zone: ZoneId = this.zone, year: Int = 2026): Long =
        ZonedDateTime.of(year, 7, 1, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun game(
        id: String,
        rating: Int? = null,
        completedAt: Long? = at(6, 15),
        status: GameStatus = GameStatus.TERMINE,
        hours: Int = 0,
        releaseYear: Int? = null,
        platform: String = "PC",
        playedPlatforms: Set<String> = emptySet(),
        abandonedAt: Long? = null,
    ) = Game(
        id = id,
        title = id,
        platform = platform,
        genre = "Aventure",
        status = status,
        rating = rating,
        completedAt = if (status == GameStatus.TERMINE) completedAt else null,
        abandonedAt = abandonedAt,
        userPlaytimeHours = hours,
        releaseYear = releaseYear,
        playedPlatforms = playedPlatforms,
    )

    private fun ids(list: List<Game>) = list.map { it.id }

    private fun slideKeys(games: List<Game>, year: Int = 2026) =
        buildRecapSlides(games, year, zone).map { it.key }

    // --- Coups de cœur ---

    @Test
    fun `huit jeux a cinq etoiles donnent huit coups de coeur`() {
        val games = (1..8).map { game("g$it", rating = 5, completedAt = at(1, it)) }
        assertEquals(8, selectFavorites(games, 2026).size)
    }

    @Test
    fun `douze jeux a cinq etoiles gardent les dix termines le plus recemment`() {
        val games = (1..12).map { game("g$it", rating = 5, completedAt = at(1, it)) }
        assertEquals((12 downTo 3).map { "g$it" }, ids(selectFavorites(games, 2026)))
    }

    @Test
    fun `un cinq etoiles et trois quatre etoiles donnent le cinq puis les deux quatre les plus recents`() {
        val games = listOf(
            game("cinq", rating = 5, completedAt = at(1, 1)),
            game("quatre-vieux", rating = 4, completedAt = at(2, 1)),
            game("quatre-recent", rating = 4, completedAt = at(4, 1)),
            game("quatre-milieu", rating = 4, completedAt = at(3, 1)),
        )
        assertEquals(listOf("cinq", "quatre-recent", "quatre-milieu"), ids(selectFavorites(games, 2026)))
    }

    @Test
    fun `deux cinq etoiles sont completes par un seul quatre etoiles`() {
        val games = listOf(
            game("cinq-a", rating = 5, completedAt = at(1, 1)),
            game("cinq-b", rating = 5, completedAt = at(1, 2)),
            game("quatre-a", rating = 4, completedAt = at(2, 1)),
            game("quatre-b", rating = 4, completedAt = at(3, 1)),
        )
        assertEquals(listOf("cinq-b", "cinq-a", "quatre-b"), ids(selectFavorites(games, 2026)))
    }

    @Test
    fun `aucun cinq etoiles et cinq quatre etoiles donnent trois coups de coeur les plus recents`() {
        val games = (1..5).map { game("g$it", rating = 4, completedAt = at(1, it)) }
        assertEquals(listOf("g5", "g4", "g3"), ids(selectFavorites(games, 2026)))
    }

    @Test
    fun `trois cinq etoiles ne sont pas completes par des quatre`() {
        val games = listOf(
            game("a", rating = 5, completedAt = at(1, 1)),
            game("b", rating = 5, completedAt = at(1, 2)),
            game("c", rating = 5, completedAt = at(1, 3)),
            game("d", rating = 4, completedAt = at(1, 4)),
        )
        assertEquals(listOf("c", "b", "a"), ids(selectFavorites(games, 2026)))
    }

    @Test
    fun `aucun quatre ni cinq donne une liste vide`() {
        val games = listOf(game("a", rating = 3), game("b", rating = 1), game("c", rating = null))
        assertTrue(selectFavorites(games, 2026).isEmpty())
    }

    @Test
    fun `jeux non notes, d'une autre annee et non termines sont ignores`() {
        val games = listOf(
            game("non-note", rating = null),
            game("autre-annee", rating = 5, completedAt = at(6, 1, year = 2025)),
            game("en-cours", rating = 5, status = GameStatus.EN_COURS),
            game("abandonne", rating = 5, status = GameStatus.ABANDONNE, abandonedAt = at(6, 1)),
            game("bon", rating = 5),
        )
        assertEquals(listOf("bon"), ids(selectFavorites(games, 2026)))
    }

    @Test
    fun `une note modifiee change la selection`() {
        val avant = listOf(game("a", rating = 3))
        assertTrue(selectFavorites(avant, 2026).isEmpty())

        val apres = listOf(game("a", rating = 5))
        assertEquals(listOf("a"), ids(selectFavorites(apres, 2026)))
    }

    @Test
    fun `colonnes des coups de coeur - deux jusqu'a quatre jeux, trois au-dela`() {
        assertEquals(2, favoritesGridColumns(1))
        assertEquals(2, favoritesGridColumns(4))
        assertEquals(3, favoritesGridColumns(5))
        assertEquals(3, favoritesGridColumns(10))
    }

    // --- Plus longs ---

    @Test
    fun `les plus longs sont classes par duree decroissante`() {
        val games = listOf(
            game("court", hours = 10),
            game("long", hours = 80),
            game("moyen", hours = 40),
        )
        assertEquals(listOf("long", "moyen", "court"), ids(selectLongest(games, 2026)))
    }

    @Test
    fun `a duree egale le termine le plus recemment passe devant puis le titre`() {
        val games = listOf(
            game("b-ancien", hours = 20, completedAt = at(1, 1)),
            game("a-recent", hours = 20, completedAt = at(5, 1)),
            game("a-meme-jour", hours = 20, completedAt = at(1, 1)),
        )
        assertEquals(listOf("a-recent", "a-meme-jour", "b-ancien"), ids(selectLongest(games, 2026)))
    }

    @Test
    fun `les durees nulles sont ignorees`() {
        val games = listOf(game("zero", hours = 0), game("a", hours = 5), game("b", hours = 7))
        assertEquals(listOf("b", "a"), ids(selectLongest(games, 2026)))
    }

    @Test
    fun `le classement est plafonne a cinq`() {
        val games = (1..8).map { game("g$it", hours = it * 10) }
        assertEquals(listOf("g8", "g7", "g6", "g5", "g4"), ids(selectLongest(games, 2026)))
    }

    @Test
    fun `moins de deux jeux avec une duree donnent une liste vide`() {
        assertTrue(selectLongest(emptyList(), 2026).isEmpty())
        assertTrue(selectLongest(listOf(game("seul", hours = 30), game("zero", hours = 0)), 2026).isEmpty())
        assertEquals(2, selectLongest(listOf(game("a", hours = 30), game("b", hours = 1)), 2026).size)
    }

    @Test
    fun `les plus longs ignorent les autres annees et les jeux non termines`() {
        val games = listOf(
            game("autre-annee", hours = 300, completedAt = at(6, 1, year = 2025)),
            game("en-cours", hours = 200, status = GameStatus.EN_COURS),
            game("a", hours = 10),
            game("b", hours = 20),
        )
        assertEquals(listOf("b", "a"), ids(selectLongest(games, 2026)))
    }

    // --- Dates posées à la main ---

    @Test
    fun `midi pile le 1er juillet est une date approximative`() {
        assertTrue(isApproximateTimestamp(approximate(), zone))
    }

    @Test
    fun `une seconde apres midi n'est plus approximatif`() {
        assertFalse(isApproximateTimestamp(approximate() + 1_000L, zone))
    }

    @Test
    fun `une milliseconde avant midi n'est pas approximatif`() {
        assertFalse(isApproximateTimestamp(approximate() - 1L, zone))
    }

    @Test
    fun `midi un autre jour n'est pas approximatif`() {
        val twoJuly = ZonedDateTime.of(2026, 7, 2, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val firstJune = ZonedDateTime.of(2026, 6, 1, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertFalse(isApproximateTimestamp(twoJuly, zone))
        assertFalse(isApproximateTimestamp(firstJune, zone))
    }

    @Test
    fun `l'annee n'importe pas pour une date approximative`() {
        assertTrue(isApproximateTimestamp(approximate(year = 2019), zone))
    }

    @Test
    fun `le fuseau decide si midi est midi`() {
        val paris = ZoneId.of("Europe/Paris")
        val tokyo = ZoneId.of("Asia/Tokyo")
        val parisNoon = approximate(zone = paris)
        assertTrue(isApproximateTimestamp(parisNoon, paris))
        assertFalse(isApproximateTimestamp(parisNoon, tokyo))
        assertTrue(isApproximateTimestamp(approximate(zone = tokyo), tokyo))
    }

    // --- Faits ---

    @Test
    fun `les dates approximatives sont exclues du premier, du dernier et du mois le plus charge`() {
        val games = listOf(
            game("approx-a", completedAt = approximate()),
            game("approx-b", completedAt = approximate()),
            game("approx-c", completedAt = approximate()),
            game("exact-mars", completedAt = at(3, 10)),
            game("exact-mai", completedAt = at(5, 20)),
        )
        val facts = computeRecapFacts(games, 2026, zone)
        assertEquals("exact-mars", facts.first?.game?.id)
        assertEquals("exact-mai", facts.last?.game?.id)
        // Juillet compterait trois jeux s'ils étaient pris en compte.
        assertEquals(1, facts.busiestMonth?.count)
        assertEquals(Month.MAY, facts.busiestMonth?.month)
    }

    @Test
    fun `premier et dernier portent la date du jour dans le fuseau`() {
        val games = listOf(
            game("premier", completedAt = at(1, 12)),
            game("dernier", completedAt = at(11, 3)),
        )
        val facts = computeRecapFacts(games, 2026, zone)
        assertEquals(LocalDate.of(2026, 1, 12), facts.first?.date)
        assertEquals(LocalDate.of(2026, 11, 3), facts.last?.date)
    }

    @Test
    fun `a egalite de mois le plus recent l'emporte`() {
        val games = listOf(
            game("mars-1", completedAt = at(3, 1, hour = 9)),
            game("mars-2", completedAt = at(3, 2)),
            game("octobre-1", completedAt = at(10, 5)),
            game("octobre-2", completedAt = at(10, 6)),
            game("juin", completedAt = at(6, 6)),
        )
        val busiest = computeRecapFacts(games, 2026, zone).busiestMonth
        assertEquals(Month.OCTOBER, busiest?.month)
        assertEquals(2, busiest?.count)
    }

    @Test
    fun `le mois le plus charge est celui qui compte le plus de jeux`() {
        val games = listOf(
            game("a", completedAt = at(2, 3)),
            game("b", completedAt = at(4, 3)),
            game("c", completedAt = at(4, 9)),
            game("d", completedAt = at(4, 20)),
            game("e", completedAt = at(9, 1)),
        )
        val busiest = computeRecapFacts(games, 2026, zone).busiestMonth
        assertEquals(Month.APRIL, busiest?.month)
        assertEquals(3, busiest?.count)
    }

    @Test
    fun `tout approximatif - ni premier, ni dernier, ni mois, mais le plus ancien reste`() {
        val games = listOf(
            game("recent", completedAt = approximate(), releaseYear = 2020),
            game("ancien", completedAt = approximate(), releaseYear = 1998),
        )
        val facts = computeRecapFacts(games, 2026, zone)
        assertNull(facts.first)
        assertNull(facts.last)
        assertNull(facts.busiestMonth)
        assertEquals("ancien", facts.oldest?.id)
        assertFalse(facts.isEmpty)
    }

    @Test
    fun `sans annee de sortie il n'y a pas de jeu le plus ancien`() {
        val games = listOf(game("a", releaseYear = null), game("b", releaseYear = null))
        assertNull(computeRecapFacts(games, 2026, zone).oldest)
    }

    @Test
    fun `les jeux sans annee de sortie sont ignores pour le plus ancien`() {
        val games = listOf(
            game("sans-annee", releaseYear = null),
            game("avec-annee", releaseYear = 2015),
        )
        assertEquals("avec-annee", computeRecapFacts(games, 2026, zone).oldest?.id)
    }

    @Test
    fun `a egalite d'annee de sortie le plus ancien est le termine le plus recemment`() {
        val games = listOf(
            game("termine-tot", releaseYear = 2010, completedAt = at(2, 1)),
            game("termine-tard", releaseYear = 2010, completedAt = at(9, 1)),
            game("plus-recent", releaseYear = 2024, completedAt = at(10, 1)),
        )
        assertEquals("termine-tard", computeRecapFacts(games, 2026, zone).oldest?.id)
    }

    @Test
    fun `un seul jeu a date exacte donne un premier mais pas de dernier`() {
        val games = listOf(
            game("exact", completedAt = at(4, 4)),
            game("approx", completedAt = approximate()),
        )
        val facts = computeRecapFacts(games, 2026, zone)
        assertEquals("exact", facts.first?.game?.id)
        assertNull(facts.last)
        assertEquals(1, facts.busiestMonth?.count)
    }

    @Test
    fun `aucun jeu donne des faits vides`() {
        assertTrue(computeRecapFacts(emptyList(), 2026, zone).isEmpty)
    }

    @Test
    fun `les faits ignorent les autres annees et les jeux non termines`() {
        val games = listOf(
            game("autre-annee", completedAt = at(2, 2, year = 2025), releaseYear = 1990),
            game("en-cours", status = GameStatus.EN_COURS, releaseYear = 1980),
            game("seul", completedAt = at(8, 8), releaseYear = 2001),
        )
        val facts = computeRecapFacts(games, 2026, zone)
        assertEquals("seul", facts.first?.game?.id)
        assertEquals("seul", facts.oldest?.id)
    }

    // --- Dates en français ---

    @Test
    fun `les dates sont formatees en francais avec 1er pour le premier du mois`() {
        assertEquals("12 janvier", formatRecapDate(LocalDate.of(2026, 1, 12), Locale.FRENCH))
        assertEquals("1er mars", formatRecapDate(LocalDate.of(2026, 3, 1), Locale.FRENCH))
        assertEquals("31 décembre", formatRecapDate(LocalDate.of(2026, 12, 31), Locale.FRENCH))
        assertEquals("janvier", monthName(Month.JANUARY, Locale.FRENCH))
    }

    // --- Mosaïque ---

    @Test
    fun `la mosaique est chronologique, egalite par titre puis id`() {
        val games = listOf(
            game("c", completedAt = at(5, 1)),
            game("b", completedAt = at(2, 1)),
            game("a", completedAt = at(2, 1)),
            game("autre-annee", completedAt = at(1, 1, year = 2025)),
            game("en-cours", status = GameStatus.EN_COURS),
        )
        assertEquals(listOf("a", "b", "c"), ids(mosaicGames(games, 2026)))
    }

    // --- Plateformes ---

    @Test
    fun `les plateformes sont triees par nombre decroissant puis par nom`() {
        val sorted = sortedPlatformCounts(mapOf("Switch" to 2, "PC" to 5, "PS5" to 2, "Xbox" to 1))
        assertEquals(
            listOf("PC" to 5, "PS5" to 2, "Switch" to 2, "Xbox" to 1),
            sorted.map { it.platform to it.count },
        )
    }

    // --- Liste de slides ---

    @Test
    fun `une annee sans jeu n'a aucune slide`() {
        assertTrue(slideKeys(emptyList()).isEmpty())
        assertTrue(slideKeys(listOf(game("autre", completedAt = at(6, 1, year = 2025)))).isEmpty())
        assertTrue(slideKeys(listOf(game("a-faire", status = GameStatus.A_FAIRE))).isEmpty())
    }

    @Test
    fun `une annee avec seulement des jeux abandonnes n'a que la slide Total`() {
        val games = listOf(
            game("a", status = GameStatus.ABANDONNE, abandonedAt = at(3, 3), rating = 5, hours = 40),
            game("b", status = GameStatus.ABANDONNE, abandonedAt = at(4, 4), hours = 20),
        )
        val slides = buildRecapSlides(games, 2026, zone)
        assertEquals(listOf("total"), slides.map { it.key })
        val total = slides.single() as RecapSlide.Total
        assertEquals(0, total.completedCount)
        assertEquals(2, total.abandonedCount)
    }

    @Test
    fun `un seul jeu termine sans note ni duree ni plateforme donne le total, les faits et la mosaique`() {
        val games = listOf(game("a", completedAt = at(5, 5), releaseYear = 2019, platform = ""))
        assertEquals(listOf("total", "facts", "mosaic"), slideKeys(games))
    }

    @Test
    fun `toutes les slides sont presentes dans l'ordre quand les donnees existent`() {
        val games = listOf(
            game("a", rating = 5, hours = 30, completedAt = at(2, 2), releaseYear = 2015),
            game("b", rating = 4, hours = 12, completedAt = at(6, 6), releaseYear = 2020, platform = "Switch"),
            game("c", status = GameStatus.ABANDONNE, abandonedAt = at(7, 7)),
        )
        assertEquals(
            listOf("total", "favorites", "longest", "platforms", "facts", "mosaic"),
            slideKeys(games),
        )
    }

    @Test
    fun `les slides sans donnee sont omises`() {
        // Pas de note : pas de coups de cœur. Une seule durée : pas de classement.
        val games = listOf(
            game("a", hours = 30, completedAt = at(2, 2)),
            game("b", completedAt = at(6, 6)),
        )
        assertEquals(listOf("total", "platforms", "facts", "mosaic"), slideKeys(games))
    }

    @Test
    fun `la slide Faits est omise quand aucune carte n'a de donnee`() {
        // Tout à date approximative et sans année de sortie : plus aucun fait.
        val games = listOf(
            game("a", completedAt = approximate(), releaseYear = null),
            game("b", completedAt = approximate(), releaseYear = null),
        )
        // Mono-plateforme ("PC") : la slide Plateformes reste, seule la slide Faits disparaît.
        assertEquals(listOf("total", "platforms", "mosaic"), slideKeys(games))
    }

    @Test
    fun `le total vient de computeStats - termines, heures et abandonnes de l'annee`() {
        val games = listOf(
            game("a", hours = 30, completedAt = at(2, 2)),
            game("b", hours = 12, completedAt = at(6, 6)),
            game("autre-annee", hours = 99, completedAt = at(6, 6, year = 2025)),
            game("abandonne", status = GameStatus.ABANDONNE, abandonedAt = at(8, 8), hours = 7),
            game("abandonne-autre-annee", status = GameStatus.ABANDONNE, abandonedAt = at(8, 8, year = 2025)),
        )
        val total = buildRecapSlides(games, 2026, zone).first() as RecapSlide.Total
        val stats = computeStats(games, selectedYear = 2026)
        assertEquals(stats.completedCount, total.completedCount)
        assertEquals(stats.totalHoursPlayed, total.totalHours)
        assertEquals(2, total.completedCount)
        assertEquals(42, total.totalHours)
        assertEquals(1, total.abandonedCount)
        assertEquals(2026, total.year)
    }

    @Test
    fun `la slide Plateformes suit les plateformes jouees et le tri`() {
        val games = listOf(
            game("a", platform = "PC/PS5", playedPlatforms = setOf("PS5"), completedAt = at(1, 1)),
            game("b", platform = "PC/PS5", playedPlatforms = setOf("PC", "PS5"), completedAt = at(1, 2)),
            game("c", platform = "Switch", completedAt = at(1, 3)),
        )
        val platforms = buildRecapSlides(games, 2026, zone).filterIsInstance<RecapSlide.Platforms>().single()
        assertEquals(
            listOf("PS5" to 2, "PC" to 1, "Switch" to 1),
            platforms.counts.map { it.platform to it.count },
        )
        assertEquals(2, platforms.maxCount)
    }

    @Test
    fun `la slide Coups de coeur porte ses colonnes`() {
        val few = (1..4).map { game("g$it", rating = 5, completedAt = at(1, it)) }
        val many = (1..6).map { game("g$it", rating = 5, completedAt = at(1, it)) }
        assertEquals(2, buildRecapSlides(few, 2026, zone).filterIsInstance<RecapSlide.Favorites>().single().columns)
        assertEquals(3, buildRecapSlides(many, 2026, zone).filterIsInstance<RecapSlide.Favorites>().single().columns)
    }

    @Test
    fun `chaque slide a une cle stable et distincte`() {
        val games = listOf(
            game("a", rating = 5, hours = 30, completedAt = at(2, 2), releaseYear = 2015),
            game("b", rating = 5, hours = 12, completedAt = at(6, 6)),
        )
        val keys = slideKeys(games)
        assertEquals(keys.distinct(), keys)
        assertEquals(keys, slideKeys(games))
    }

    @Test
    fun `la mosaique ne contient que les jeux termines de l'annee`() {
        val games = listOf(
            game("a", completedAt = at(2, 2)),
            game("abandonne", status = GameStatus.ABANDONNE, abandonedAt = at(3, 3)),
        )
        val mosaic = buildRecapSlides(games, 2026, zone).filterIsInstance<RecapSlide.Mosaic>().single()
        assertEquals(listOf("a"), ids(mosaic.games))
    }
}
